package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingApplyGuardTest {
    @Test
    fun unchangedTrackingInputsRemainApplicable() {
        val clip = videoClip()
        val captured = ObjectTrackingApplyGuard.capture(clip)

        assertTrue(ObjectTrackingApplyGuard.canApply(captured, clip.copy()))
        assertTrue(ObjectTrackingApplyGuard.canApply(captured, clip.copy(volume = 0.25)))
    }

    @Test
    fun changedTrimOrMissingClipFailsClosed() {
        val clip = videoClip()
        val captured = ObjectTrackingApplyGuard.capture(clip)

        assertFalse(ObjectTrackingApplyGuard.canApply(captured, null))
        assertFalse(ObjectTrackingApplyGuard.canApply(captured, clip.copy(sourceInUs = clip.sourceInUs + 1L)))
        assertFalse(ObjectTrackingApplyGuard.canApply(captured, clip.copy(sourceOutUs = clip.sourceOutUs - 1L)))
        assertFalse(ObjectTrackingApplyGuard.canApply(captured, clip.copy(durationUs = clip.durationUs - 1L)))
        assertFalse(ObjectTrackingApplyGuard.canApply(captured, clip.copy(assetId = "asset-2")))
    }

    private fun videoClip() = Clip(
        id = "clip-1",
        assetId = "asset-1",
        startUs = 2_000_000L,
        durationUs = 1_000_000L,
        sourceInUs = 250_000L,
        sourceOutUs = 1_250_000L,
    )
}
