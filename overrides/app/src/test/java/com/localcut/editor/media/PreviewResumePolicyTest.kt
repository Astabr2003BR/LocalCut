package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewResumePolicyTest {
    @Test
    fun pausedPreviewWithMediaRefreshesFrameAfterResume() {
        assertTrue(PreviewResumePolicy.shouldRefresh(durationUs = 5_000_000L, playing = false))
    }

    @Test
    fun playingPreviewDoesNotNeedForcedRefresh() {
        assertFalse(PreviewResumePolicy.shouldRefresh(durationUs = 5_000_000L, playing = true))
    }

    @Test
    fun emptyProjectDoesNotRefresh() {
        assertFalse(PreviewResumePolicy.shouldRefresh(durationUs = 0L, playing = false))
    }
}
