package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingSelectionAnchorPolicyTest {
    @Test
    fun anchorIsAlwaysClipPresentationStart() {
        val clip = clip(startUs = 2_500_000L)
        assertEquals(2_500_000L, ObjectTrackingSelectionAnchorPolicy.anchorProjectTimeUs(clip))
    }

    @Test
    fun captureAllowsAtMostOneFrameFromClipStart() {
        val clip = clip(startUs = 1_000_000L)
        assertTrue(ObjectTrackingSelectionAnchorPolicy.canCapture(1_000_000L, clip, fps = 30))
        assertTrue(ObjectTrackingSelectionAnchorPolicy.canCapture(1_033_333L, clip, fps = 30))
        assertFalse(ObjectTrackingSelectionAnchorPolicy.canCapture(1_033_334L, clip, fps = 30))
        assertFalse(ObjectTrackingSelectionAnchorPolicy.canCapture(999_999L, clip, fps = 30))
    }

    @Test
    fun invalidFpsFailsClosed() {
        val error = runCatching {
            ObjectTrackingSelectionAnchorPolicy.canCapture(0L, clip(startUs = 0L), fps = 0)
        }.exceptionOrNull()
        assertEquals("OBJECT_TRACKING_FPS_INVALID", error?.message)
    }

    private fun clip(startUs: Long) = Clip(
        id = "clip-1",
        assetId = "asset-1",
        startUs = startUs,
        durationUs = 1_000_000L,
        sourceInUs = 0L,
        sourceOutUs = 1_000_000L,
    )
}
