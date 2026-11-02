package com.localcut.editor.audio

import com.localcut.editor.domain.model.*
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDspTest {
    @Test
    fun hardLeftPanPreservesLeftAndSilencesRight() {
        val input = floatArrayOf(0.5f, 0.5f, -0.25f, -0.25f)
        val output = AudioDsp.processInterleaved(
            samples = input,
            channelCount = 2,
            sampleRateHz = 48_000,
            settings = AudioProcessingSettings(pan = -1.0),
        )

        assertEquals(0.5f, output[0], 1e-6f)
        assertEquals(0f, output[1], 1e-6f)
        assertEquals(-0.25f, output[2], 1e-6f)
        assertEquals(0f, output[3], 1e-6f)
    }

    @Test
    fun limiterCapsPeakAtConfiguredCeiling() {
        val ceiling = 10.0.pow(-6.0 / 20.0).toFloat()
        val output = AudioDsp.processInterleaved(
            samples = floatArrayOf(1f, -1f, 0.2f, -0.2f),
            channelCount = 1,
            sampleRateHz = 48_000,
            settings = AudioProcessingSettings(limiter = LimiterSettings(ceilingDb = -6.0)),
        )

        assertTrue(output.all { kotlin.math.abs(it) <= ceiling + 1e-5f })
    }

    @Test
    fun compressorReducesSustainedSignalAboveThreshold() {
        val input = FloatArray(48_000) { 0.9f }
        val output = AudioDsp.processInterleaved(
            samples = input,
            channelCount = 1,
            sampleRateHz = 48_000,
            settings = AudioProcessingSettings(
                compressor = CompressorSettings(
                    thresholdDb = -12.0,
                    ratio = 8.0,
                    attackMs = 1.0,
                    releaseMs = 100.0,
                ),
            ),
        )

        assertTrue(output.last() < 0.5f)
        assertTrue(output.all { it.isFinite() })
    }

    @Test
    fun zeroDbNeutralSettingsPreserveSamples() {
        val input = floatArrayOf(-0.75f, -0.1f, 0f, 0.25f, 0.8f)
        val output = AudioDsp.processInterleaved(input, 1, 48_000, AudioProcessingSettings())

        input.indices.forEach { assertEquals(input[it], output[it], 1e-6f) }
    }
}
