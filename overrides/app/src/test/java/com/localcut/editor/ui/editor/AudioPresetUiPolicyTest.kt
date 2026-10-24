package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AudioProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPresetUiPolicyTest {
    @Test
    fun everyPresetProducesValidRenderedAudioSettings() {
        AudioPreset.entries.forEach { preset ->
            val result = AudioPresetUiPolicy.apply(AudioProcessingSettings(), preset)
            assertTrue("$preset must validate", result.validationIssues().isEmpty())
            assertTrue("$preset must configure EQ", result.eqBands.isNotEmpty())
            assertTrue("$preset must configure compressor", result.compressor != null)
            assertTrue("$preset must configure limiter", result.limiter != null)
        }
    }

    @Test
    fun presetsPreserveUserPanWhileReplacingProcessingStack() {
        val source = AudioProcessingSettings(pan = -0.35, normalizationGainDb = -4.0)
        val result = AudioPresetUiPolicy.apply(source, AudioPreset.PODCAST)

        assertEquals(-0.35, result.pan, 0.0)
        assertNotEquals(source.normalizationGainDb, result.normalizationGainDb, 0.0)
        assertTrue(result.noiseGate != null)
    }

    @Test
    fun musicAndBassPresetsDoNotEnableNoiseGate() {
        assertTrue(AudioPresetUiPolicy.apply(AudioProcessingSettings(), AudioPreset.BASS_BOOST).noiseGate == null)
        assertTrue(AudioPresetUiPolicy.apply(AudioProcessingSettings(), AudioPreset.MUSIC_PUNCH).noiseGate == null)
    }

    @Test
    fun labelsAreShortAndMobileFriendly() {
        AudioPreset.entries.forEach { preset ->
            val label = AudioPresetUiPolicy.label(preset)
            assertTrue(label.isNotBlank())
            assertTrue(label.length <= 12)
        }
    }
}
