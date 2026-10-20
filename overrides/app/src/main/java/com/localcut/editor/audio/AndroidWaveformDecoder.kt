package com.localcut.editor.audio

import android.content.ContentResolver
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AndroidWaveformDecoder(
    private val contentResolver: ContentResolver,
) : WaveformDecoder {
    override fun decode(
        uri: String,
        sourceInUs: Long,
        sourceOutUs: Long,
        bucketCount: Int,
    ): Result<WaveformData> = runCatching {
        require(sourceInUs >= 0L) { "sourceInUs must not be negative" }
        require(sourceOutUs > sourceInUs) { "sourceOutUs must be greater than sourceInUs" }
        require(bucketCount > 0) { "bucketCount must be positive" }

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
            } ?: error("No audio track available for waveform")

            extractor.selectTrack(trackIndex)
            extractor.seekTo(sourceInUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME type missing")
            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(inputFormat, null, null, 0)
                it.start()
            }

            val accumulator = WaveformAccumulator(sourceInUs, sourceOutUs, bucketCount)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0L || sampleTimeUs >= sourceOutUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, maxOf(sourceInUs, sourceOutUs), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val inputBuffer = codec.getInputBuffer(inputIndex) ?: error("Decoder input buffer unavailable")
                            val size = extractor.readSampleData(inputBuffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, sampleTimeUs.coerceAtLeast(sourceInUs), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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
                            codec.getOutputBuffer(outputIndex)?.let { buffer ->
                                accumulateOutput(
                                    buffer = buffer,
                                    offset = bufferInfo.offset,
                                    size = bufferInfo.size,
                                    presentationTimeUs = bufferInfo.presentationTimeUs,
                                    format = outputFormat,
                                    accumulator = accumulator,
                                )
                            }
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            accumulator.toData()
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
        format: MediaFormat,
        accumulator: WaveformAccumulator,
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
                val absolute = kotlin.math.abs(amplitude)
                if (absolute > peak) peak = absolute
            }
            val frameOffsetUs = frame.toLong() * 1_000_000L / sampleRate.toLong()
            accumulator.addPeakAt(presentationTimeUs + frameOffsetUs, peak)
        }
    }

    private fun MediaFormat.integerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
    }
}
