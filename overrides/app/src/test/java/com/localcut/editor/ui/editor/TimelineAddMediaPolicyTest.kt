package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineAddMediaPolicyTest {
    @Test
    fun exclusiveVisualTrackAddsVideoOrImage() {
        assertEquals(
            TimelineAddTarget.MEDIA,
            TimelineAddMediaPolicy.targetFor(TrackType.VISUAL, TrackCollisionPolicy.EXCLUSIVE),
        )
    }

    @Test
    fun overlayVisualTrackAddsOverlayMedia() {
        assertEquals(
            TimelineAddTarget.OVERLAY,
            TimelineAddMediaPolicy.targetFor(TrackType.VISUAL, TrackCollisionPolicy.OVERLAY),
        )
    }

    @Test
    fun audioTrackAddsAudio() {
        assertEquals(
            TimelineAddTarget.AUDIO,
            TimelineAddMediaPolicy.targetFor(TrackType.AUDIO, TrackCollisionPolicy.EXCLUSIVE),
        )
    }
}
