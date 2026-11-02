package com.localcut.editor.ui.editor

enum class AudioToolsPanelSection {
    BASIC,
    EQ,
    DYNAMICS,
}

object AudioToolsPanelUiPolicy {
    fun sections(): List<AudioToolsPanelSection> = listOf(
        AudioToolsPanelSection.BASIC,
        AudioToolsPanelSection.EQ,
        AudioToolsPanelSection.DYNAMICS,
    )

    fun defaultSection(): AudioToolsPanelSection = AudioToolsPanelSection.BASIC
}
