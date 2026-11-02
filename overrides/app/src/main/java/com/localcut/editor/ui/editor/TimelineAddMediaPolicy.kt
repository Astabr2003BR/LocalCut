package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType

enum class TimelineAddTarget {
    MEDIA,
    OVERLAY,
    AUDIO,
}

object TimelineAddMediaPolicy {
    fun targetFor(
        trackType: TrackType,
        collisionPolicy: TrackCollisionPolicy,
    ): TimelineAddTarget = when {
        trackType == TrackType.AUDIO -> TimelineAddTarget.AUDIO
        collisionPolicy == TrackCollisionPolicy.OVERLAY -> TimelineAddTarget.OVERLAY
        else -> TimelineAddTarget.MEDIA
    }
}
