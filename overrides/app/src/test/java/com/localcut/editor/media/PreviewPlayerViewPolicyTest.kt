package com.localcut.editor.media

import androidx.media3.ui.AspectRatioFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlayerViewPolicyTest {
    @Test
    fun playerViewUsesExplicitFitResizeMode() {
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            PreviewPlayerViewPolicy.RESIZE_MODE,
        )
    }

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

    @Test
    fun surfaceRefreshDoesNotReassignAnAlreadyBoundPreviewPlayer() {
        assertFalse(
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = true,
            ),
        )
    }

    @Test
    fun surfaceRefreshAssignsPreviewPlayerWhenViewLostItsBinding() {
        assertTrue(
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = false,
            ),
        )
    }
}
