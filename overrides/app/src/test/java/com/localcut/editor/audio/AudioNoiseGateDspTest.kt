package com.localcut.editor.audio

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.NoiseGateSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioNoiseGateDspTest {
    @Test
    fun sustainedSignalBelowThresholdIsAttenuatedByGate() {
        val input = FloatArray(4_800) { 0.001f }
        val output = AudioDsp.processInterleaved(
            samples = input,
            channelCount = 1,
            sampleRateHz = 48_000,
            settings = AudioProcessingSettings(
                noiseGate = NoiseGateSettings(
                    thresholdDb = -40.0,
                    attackMs = 1.0,
                    releaseMs = 1.0,
                ),
            ),
        )

        assertTrue(output.last() < input.last() * 0.1f)
        assertTrue(output.all { it.isFinite() })
    }
}
