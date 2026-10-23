package com.localcut.editor.audio

import android.content.ContentResolver
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.localcut.editor.domain.model.BeatMarker
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class AndroidBeatAnalyzer(
    private val contentResolver: ContentResolver,
) {
    fun analyze(
        uri: String,
        sourceInUs: Long,
        sourceOutUs: Long,
        settings: BeatDetectionSettings = BeatDetectionSettings(),
    ): Result<List<BeatMarker>> = runCatching {
        require(sourceInUs >= 0L) { "sourceInUs must not be negative" }
        require(sourceOutUs > sourceInUs) { "sourceOutUs must be greater than sourceInUs" }
        require(settings.validationIssues().isEmpty()) { "invalid beat detection settings" }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                if (descriptor.length >= 0L) {
                    extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                } else {
                    extractor.setDataSource(descriptor.fileDescriptor)
                }
            } ?: error("Unable to open media URI")

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track available for beat analysis")

            extractor.selectTrack(trackIndex)
            extractor.seekTo(sourceInUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME type missing")
            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(inputFormat, null, null, 0)
                it.start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat
            var accumulator: DownsamplePeakAccumulator? = null

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0L || sampleTimeUs >= sourceOutUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, sourceOutUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: error("Decoder input buffer unavailable")
                            val size = extractor.readSampleData(inputBuffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    sampleTimeUs.coerceAtLeast(sourceInUs),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, sampleTimeUs, extractor.sampleFlags)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            val sampleRate = outputFormat.integerOrNull(MediaFormat.KEY_SAMPLE_RATE)
                                ?: error("Decoded sample rate missing")
                            val channelCount = outputFormat.integerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                                ?: error("Decoded channel count missing")
                            require(sampleRate > 0 && channelCount > 0) { "Invalid decoded audio format" }
                            val activeAccumulator = accumulator ?: DownsamplePeakAccumulator(
                                sourceSampleRateHz = sampleRate,
                                targetSampleRateHz = TARGET_ANALYSIS_RATE_HZ,
                                maxSamples = MAX_ANALYSIS_SAMPLES,
                            ).also { accumulator = it }
                            require(activeAccumulator.sourceSampleRateHz == sampleRate) {
                                "Audio sample rate changed during beat analysis"
                            }
                            codec.getOutputBuffer(outputIndex)?.let { buffer ->
                                accumulateOutput(
                                    buffer = buffer,
                                    offset = bufferInfo.offset,
                                    size = bufferInfo.size,
                                    presentationTimeUs = bufferInfo.presentationTimeUs,
                                    sourceInUs = sourceInUs,
                                    sourceOutUs = sourceOutUs,
                                    format = outputFormat,
                                    accumulator = activeAccumulator,
                                )
                            }
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val analyzed = accumulator?.finish() ?: error("No PCM audio decoded for beat analysis")
            if (analyzed.samples.isEmpty()) return@runCatching emptyList()
            BeatDetector.detect(
                monoSamples = analyzed.samples,
                sampleRateHz = analyzed.sampleRateHz,
                settings = settings,
            )
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {
                // Decoder may fail before start; release below is still required.
            }
            codec?.release()
            extractor.release()
        }
    }

    private fun accumulateOutput(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        presentationTimeUs: Long,
        sourceInUs: Long,
        sourceOutUs: Long,
        format: MediaFormat,
        accumulator: DownsamplePeakAccumulator,
    ) {
        val sampleRate = format.integerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: return
        val channelCount = format.integerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: return
        if (sampleRate <= 0 || channelCount <= 0 || size <= 0) return
        val encoding = format.integerOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> error("Unsupported decoder PCM encoding: $encoding")
        }
        val bytesPerFrame = bytesPerSample * channelCount
        if (bytesPerFrame <= 0) return

        val data = buffer.duplicate().apply {
            position(offset)
            limit((offset + size).coerceAtMost(capacity()))
        }.slice().order(ByteOrder.nativeOrder())
        val frameCount = data.remaining() / bytesPerFrame
        for (frame in 0 until frameCount) {
            var peak = 0f
            repeat(channelCount) {
                val amplitude = when (encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> ((data.get().toInt() and 0xFF) - 128) / 128f
                    AudioFormat.ENCODING_PCM_16BIT -> data.short / 32768f
                    AudioFormat.ENCODING_PCM_FLOAT -> data.float
                    else -> 0f
                }
                peak = max(peak, abs(amplitude))
            }
            val frameOffsetUs = frame.toLong() * 1_000_000L / sampleRate.toLong()
            val timeUs = presentationTimeUs + frameOffsetUs
            if (timeUs in sourceInUs until sourceOutUs) {
                accumulator.add(peak)
            }
        }
    }

    private fun MediaFormat.integerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
        const val TARGET_ANALYSIS_RATE_HZ = 2_000
        const val MAX_ANALYSIS_SAMPLES = 7_200_000
    }
}

internal data class DownsampledMonoAudio(
    val samples: FloatArray,
    val sampleRateHz: Int,
)

internal class DownsamplePeakAccumulator(
    val sourceSampleRateHz: Int,
    targetSampleRateHz: Int,
    private val maxSamples: Int,
) {
    private val framesPerSample = max(1, sourceSampleRateHz / targetSampleRateHz.coerceAtLeast(1))
    private val outputSampleRateHz = max(1, (sourceSampleRateHz.toDouble() / framesPerSample.toDouble()).toInt())
    private var frameCount = 0
    private var peak = 0f
    private var samples = FloatArray(4_096)
    private var size = 0

    init {
        require(sourceSampleRateHz > 0) { "source sample rate must be positive" }
        require(targetSampleRateHz > 0) { "target sample rate must be positive" }
        require(maxSamples > 0) { "maxSamples must be positive" }
    }

    fun add(amplitude: Float) {
        if (!amplitude.isFinite()) return
        peak = max(peak, abs(amplitude).coerceIn(0f, 1f))
        frameCount += 1
        if (frameCount >= framesPerSample) flushPeak()
    }

    fun finish(): DownsampledMonoAudio {
        if (frameCount > 0) flushPeak()
        return DownsampledMonoAudio(
            samples = samples.copyOf(size),
            sampleRateHz = outputSampleRateHz,
        )
    }

    private fun flushPeak() {
        require(size < maxSamples) { "Beat analysis exceeded mobile sample budget" }
        if (size == samples.size) {
            samples = samples.copyOf(minOf(maxSamples, samples.size * 2))
        }
        samples[size] = peak
        size += 1
        peak = 0f
        frameCount = 0
    }
}
