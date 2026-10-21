package com.localcut.editor.domain.model

enum class TransitionKind {
    DISSOLVE,
    FADE_TO_BLACK,
    WIPE_LEFT,
}

data class ClipTransition(
    val id: String,
    val fromClipId: String,
    val toClipId: String,
    val durationUs: Long,
    val kind: TransitionKind,
)
