package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AudioProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDynamicsUiPolicyTest {
    @Test
    fun compressorControlsUpdateOnlyEnabledCompressorWithinModelBounds() {
        val enabled = AudioToolsUiPolicy.setCompressorEnabled(AudioProcessingSettings(), true)
        val edited = enabled
            .let { AudioToolsUiPolicy.setCompressorThreshold(it, -24.0) }
            .let { AudioToolsUiPolicy.setCompressorRatio(it, 6.0) }
            .let { AudioToolsUiPolicy.setCompressorAttack(it, 18.0) }
            .let { AudioToolsUiPolicy.setCompressorRelease(it, 240.0) }
            .let { AudioToolsUiPolicy.setCompressorMakeupGain(it, 3.5) }

        val compressor = requireNotNull(edited.compressor)
        assertEquals(-24.0, compressor.thresholdDb, 0.0)
        assertEquals(6.0, compressor.ratio, 0.0)
        assertEquals(18.0, compressor.attackMs, 0.0)
        assertEquals(240.0, compressor.releaseMs, 0.0)
        assertEquals(3.5, compressor.makeupGainDb, 0.0)
        assertTrue(edited.validationIssues().isEmpty())

        assertNull(AudioToolsUiPolicy.setCompressorThreshold(AudioProcessingSettings(), -20.0).compressor)
    }

    @Test
    fun limiterUiEditsOnlyCeilingBecauseReleaseIsNotInCurrentDsp() {
        val enabled = AudioToolsUiPolicy.setLimiterEnabled(AudioProcessingSettings(), true)
        val edited = AudioToolsUiPolicy.setLimiterCeiling(enabled, -2.5)

        assertEquals(-2.5, edited.limiter?.ceilingDb ?: 0.0, 0.0)
        assertEquals(enabled.limiter?.releaseMs, edited.limiter?.releaseMs)
        assertTrue(edited.validationIssues().isEmpty())
    }

    @Test
    fun noiseGateControlsUpdateRenderedGateParameters() {
        val enabled = AudioToolsUiPolicy.setNoiseGateEnabled(AudioProcessingSettings(), true)
        val edited = enabled
            .let { AudioToolsUiPolicy.setNoiseGateThreshold(it, -42.0) }
            .let { AudioToolsUiPolicy.setNoiseGateAttack(it, 12.0) }
            .let { AudioToolsUiPolicy.setNoiseGateRelease(it, 280.0) }

        val gate = requireNotNull(edited.noiseGate)
        assertEquals(-42.0, gate.thresholdDb, 0.0)
        assertEquals(12.0, gate.attackMs, 0.0)
        assertEquals(280.0, gate.releaseMs, 0.0)
        assertTrue(edited.validationIssues().isEmpty())
    }
}
