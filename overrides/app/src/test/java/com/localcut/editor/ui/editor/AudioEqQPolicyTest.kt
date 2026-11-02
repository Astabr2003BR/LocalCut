package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AudioProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEqQPolicyTest {
    @Test
    fun standardEqBandsExposeIndependentQWithoutChangingGain() {
        val source = AudioToolsUiPolicy.setEqGain(AudioProcessingSettings(), AudioEqBand.MID, 4.0)
        val edited = AudioToolsUiPolicy.setEqQ(source, AudioEqBand.MID, 2.5)

        assertEquals(4.0, AudioToolsUiPolicy.eqGain(edited, AudioEqBand.MID), 0.0)
        assertEquals(2.5, AudioToolsUiPolicy.eqQ(edited, AudioEqBand.MID), 0.0)
        assertEquals(1.0, AudioToolsUiPolicy.eqQ(edited, AudioEqBand.LOW), 0.0)
        assertEquals(1.0, AudioToolsUiPolicy.eqQ(edited, AudioEqBand.HIGH), 0.0)
        assertTrue(edited.validationIssues().isEmpty())
    }
}
