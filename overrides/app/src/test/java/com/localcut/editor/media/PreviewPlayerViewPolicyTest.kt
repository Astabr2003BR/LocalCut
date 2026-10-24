package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlayerViewPolicyTest {
    @Test
    fun playerViewEnablesComposeSurfaceRecoveryWorkaround() {
        assertTrue(PreviewPlayerViewPolicy.ENABLE_COMPOSE_SURFACE_SYNC_WORKAROUND)
    }

    @Test
    fun playerViewKeepsLastFrameWhilePlayerResets() {
        assertTrue(PreviewPlayerViewPolicy.KEEP_CONTENT_ON_PLAYER_RESET)
    }

    @Test
    fun viewportResizeRefreshesBoundSurfaceWhenPreviewHasMedia() {
        assertTrue(
            PreviewPlayerViewPolicy.shouldRefreshAfterViewportResize(
                previousHeightDp = 292f,
                currentHeightDp = 238f,
                durationUs = 5_000_000L,
            ),
        )
    }

    @Test
    fun viewportResizeDoesNotRefreshForSameHeightOrEmptyPreview() {
        assertFalse(
            PreviewPlayerViewPolicy.shouldRefreshAfterViewportResize(
                previousHeightDp = 238f,
                currentHeightDp = 238f,
                durationUs = 5_000_000L,
            ),
        )
        assertFalse(
            PreviewPlayerViewPolicy.shouldRefreshAfterViewportResize(
                previousHeightDp = 292f,
                currentHeightDp = 238f,
                durationUs = 0L,
            ),
        )
    }

    @Test
    fun successfulContentLoadRefreshesBoundSurface() {
        assertTrue(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = true,
                durationUs = 5_000_000L,
                hasBoundView = true,
            ),
        )
    }

    @Test
    fun contentLoadDoesNotRefreshWithoutMediaViewOrSuccess() {
        assertFalse(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = false,
                durationUs = 5_000_000L,
                hasBoundView = true,
            ),
        )
        assertFalse(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = true,
                durationUs = 0L,
                hasBoundView = true,
            ),
        )
        assertFalse(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = true,
                durationUs = 5_000_000L,
                hasBoundView = false,
            ),
        )
    }
}
