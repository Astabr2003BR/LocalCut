package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.LimiterSettings
import com.localcut.editor.domain.model.NoiseGateSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioToolsUiPolicyTest {
    @Test
    fun panAndNormalizationStayInsideModelBounds() {
        val result = AudioToolsUiPolicy.setNormalizationGain(
            AudioToolsUiPolicy.setPan(AudioProcessingSettings(), -0.75),
            6.0,
        )

        assertEquals(-0.75, result.pan, 0.0)
        assertEquals(6.0, result.normalizationGainDb, 0.0)
        assertTrue(result.validationIssues().isEmpty())
    }

    @Test
    fun threeBandEqUpdatesOnlyRequestedBand() {
        val first = AudioToolsUiPolicy.setEqGain(AudioProcessingSettings(), AudioEqBand.LOW, 4.0)
        val second = AudioToolsUiPolicy.setEqGain(first, AudioEqBand.HIGH, -3.0)

        assertEquals(3, second.eqBands.size)
        assertEquals(4.0, second.eqBands[0].gainDb, 0.0)
        assertEquals(0.0, second.eqBands[1].gainDb, 0.0)
        assertEquals(-3.0, second.eqBands[2].gainDb, 0.0)
        assertTrue(second.validationIssues().isEmpty())
    }

    @Test
    fun dynamicsTogglesUseValidDefaultsAndCanBeDisabled() {
        var settings = AudioProcessingSettings()
        settings = AudioToolsUiPolicy.setCompressorEnabled(settings, true)
        settings = AudioToolsUiPolicy.setLimiterEnabled(settings, true)
        settings = AudioToolsUiPolicy.setNoiseGateEnabled(settings, true)

        assertNotNull(settings.compressor)
        assertNotNull(settings.limiter)
        assertNotNull(settings.noiseGate)
        assertTrue(settings.validationIssues().isEmpty())

        settings = AudioToolsUiPolicy.setCompressorEnabled(settings, false)
        settings = AudioToolsUiPolicy.setLimiterEnabled(settings, false)
        settings = AudioToolsUiPolicy.setNoiseGateEnabled(settings, false)
        assertEquals(null, settings.compressor)
        assertEquals(null, settings.limiter)
        assertEquals(null, settings.noiseGate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPanIsRejected() {
        AudioToolsUiPolicy.setPan(AudioProcessingSettings(), 2.0)
    }

    @Test
    fun defaultDynamicsMatchDomainDefaults() {
        assertEquals(CompressorSettings(), AudioToolsUiPolicy.setCompressorEnabled(AudioProcessingSettings(), true).compressor)
        assertEquals(LimiterSettings(), AudioToolsUiPolicy.setLimiterEnabled(AudioProcessingSettings(), true).limiter)
        assertEquals(NoiseGateSettings(), AudioToolsUiPolicy.setNoiseGateEnabled(AudioProcessingSettings(), true).noiseGate)
    }
}
