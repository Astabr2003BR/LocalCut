package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioToolsPanelUiPolicyTest {
    @Test
    fun mobileAudioPanelStartsSimpleAndKeepsAdvancedSectionsReachable() {
        assertEquals(
            listOf(AudioToolsPanelSection.BASIC, AudioToolsPanelSection.EQ, AudioToolsPanelSection.DYNAMICS),
            AudioToolsPanelUiPolicy.sections(),
        )
        assertEquals(AudioToolsPanelSection.BASIC, AudioToolsPanelUiPolicy.defaultSection())
    }
}
