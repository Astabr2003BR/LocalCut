package com.localcut.editor.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.localcut.editor.audio.StatefulAudioDsp
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.NoiseGateSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class LocalCutAudioProcessorStreamingTest {
    private val sampleRate = 48_000
    private val channelCount = 2
    private val settings = AudioProcessingSettings(
        eqBands = listOf(EqBand(frequencyHz = 1_000.0, gainDb = 5.0, q = 0.8)),
        compressor = CompressorSettings(
            thresholdDb = -15.0,
            ratio = 5.0,
            attackMs = 3.0,
            releaseMs = 90.0,
            makeupGainDb = 1.0,
        ),
        noiseGate = NoiseGateSettings(
            thresholdDb = -45.0,
            attackMs = 2.0,
            releaseMs = 40.0,
        ),
        pan = 0.2,
        normalizationGainDb = -1.5,
    )

    @Test
    fun multipleMedia3InputBuffersPreserveContinuousDspState() {
        val input = stereoSignal(frameCount = 2_400)
        val expected = StatefulAudioDsp(channelCount, sampleRate, settings).process(input)
        val processor = configuredProcessor()
        val splitA = 734 * channelCount
        val splitB = 1_531 * channelCount

        val actual = listOf(
            input.copyOfRange(0, splitA),
            input.copyOfRange(splitA, splitB),
            input.copyOfRange(splitB, input.size),
        ).flatMap { part ->
            queueFloatChunk(processor, part).asIterable()
        }.toFloatArray()

        assertArrayEquals(expected, actual, 1e-6f)
    }

    @Test
    fun flushResetsDspHistoryForSeekOrStreamDiscontinuity() {
        val input = stereoSignal(frameCount = 600)
        val processor = configuredProcessor()
        val first = queueFloatChunk(processor, input)

        queueFloatChunk(processor, stereoSignal(frameCount = 1_200))
        processor.flush()
        val afterFlush = queueFloatChunk(processor, input)

        assertArrayEquals(first, afterFlush, 1e-6f)
    }

    private fun configuredProcessor(): LocalCutAudioProcessor =
        LocalCutAudioProcessor(settings).also { processor ->
            processor.configure(
                AudioProcessor.AudioFormat(
                    sampleRate,
                    channelCount,
                    C.ENCODING_PCM_FLOAT,
                ),
            )
            processor.flush()
        }

    private fun queueFloatChunk(
        processor: LocalCutAudioProcessor,
        samples: FloatArray,
    ): FloatArray {
        val input = ByteBuffer.allocateDirect(samples.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        return FloatArray(output.remaining() / Float.SIZE_BYTES) { output.float }
    }

    private fun stereoSignal(frameCount: Int): FloatArray = FloatArray(frameCount * channelCount).also { out ->
        repeat(frameCount) { frame ->
            val t = frame.toDouble() / sampleRate
            val envelope = when {
                frame < frameCount / 4 -> 0.015
                frame < frameCount / 2 -> 0.35
                frame < frameCount * 3 / 4 -> 0.8
                else -> 0.08
            }
            out[frame * channelCount] = (sin(2.0 * PI * 440.0 * t) * envelope).toFloat()
            out[frame * channelCount + 1] =
                (sin(2.0 * PI * 660.0 * t) * envelope * 0.8).toFloat()
        }
    }
}
