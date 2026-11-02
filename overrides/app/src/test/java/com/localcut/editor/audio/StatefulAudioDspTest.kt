package com.localcut.editor.audio

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.NoiseGateSettings
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StatefulAudioDspTest {
    private val sampleRate = 48_000
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
    fun splittingInputAcrossBuffersMatchesOneContinuousBuffer() {
        val input = stereoSignal(frameCount = 2_400)
        val oneShot = StatefulAudioDsp(
            channelCount = 2,
            sampleRateHz = sampleRate,
            settings = settings,
        ).process(input)

        val streaming = StatefulAudioDsp(
            channelCount = 2,
            sampleRateHz = sampleRate,
            settings = settings,
        )
        val splitA = 734 * 2
        val splitB = 1_531 * 2
        val parts = listOf(
            input.copyOfRange(0, splitA),
            input.copyOfRange(splitA, splitB),
            input.copyOfRange(splitB, input.size),
        )
        val chunked = parts.flatMap { part -> streaming.process(part).asIterable() }.toFloatArray()

        assertArrayEquals(oneShot, chunked, 1e-6f)
    }

    @Test
    fun resetRestoresDeterministicInitialState() {
        val input = stereoSignal(frameCount = 400)
        val processor = StatefulAudioDsp(2, sampleRate, settings)

        val first = processor.process(input)
        processor.process(stereoSignal(frameCount = 800))
        processor.reset()
        val afterReset = processor.process(input)

        assertArrayEquals(first, afterReset, 1e-6f)
    }

    @Test
    fun oneShotAudioDspApiRemainsCompatibleWithStatefulSinglePass() {
        val input = stereoSignal(frameCount = 600)
        val legacy = AudioDsp.processInterleaved(input, 2, sampleRate, settings)
        val stateful = StatefulAudioDsp(2, sampleRate, settings).process(input)

        assertArrayEquals(legacy, stateful, 1e-6f)
    }

    @Test
    fun processDoesNotMutateInputAndRejectsMisalignedInterleaving() {
        val input = stereoSignal(frameCount = 20)
        val original = input.copyOf()
        val processor = StatefulAudioDsp(2, sampleRate, settings)

        processor.process(input)
        assertArrayEquals(original, input, 0f)

        val failure = runCatching { processor.process(floatArrayOf(0.1f, 0.2f, 0.3f)) }
        assertEquals("interleaved sample count must align to channel count", failure.exceptionOrNull()?.message)
    }

    private fun stereoSignal(frameCount: Int): FloatArray = FloatArray(frameCount * 2).also { out ->
        repeat(frameCount) { frame ->
            val t = frame.toDouble() / sampleRate
            val envelope = when {
                frame < frameCount / 4 -> 0.015
                frame < frameCount / 2 -> 0.35
                frame < frameCount * 3 / 4 -> 0.8
                else -> 0.08
            }
            out[frame * 2] = (sin(2.0 * PI * 440.0 * t) * envelope).toFloat()
            out[frame * 2 + 1] = (sin(2.0 * PI * 660.0 * t) * envelope * 0.8).toFloat()
        }
    }
}
