package com.localcut.editor.domain.model

enum class TrackType { VISUAL, AUDIO }

enum class TrackCollisionPolicy { EXCLUSIVE, OVERLAY }

data class Track(
    val id: String,
    val type: TrackType,
    val order: Int,
    val collisionPolicy: TrackCollisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
    val muted: Boolean = false,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val clips: List<Clip> = emptyList(),
    val solo: Boolean = false,
)
