package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBlackFrameRecoverySequenceTest {
    @Test
    fun externalPickerReturnKeepsPlayerAndRebindsVideoOutputOnlyWhenSurfaceIsReady() {
        assertTrue(PreviewPlayerViewPolicy.ENABLE_COMPOSE_SURFACE_SYNC_WORKAROUND)
        assertTrue(PreviewPlayerViewPolicy.KEEP_CONTENT_ON_PLAYER_RESET)

        assertFalse(
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = true,
            ),
        )
        assertTrue(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 7_000_000L,
                hasBoundView = true,
                surfaceReady = true,
            ),
        )
        assertFalse(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 7_000_000L,
                hasBoundView = true,
                surfaceReady = false,
            ),
        )

        assertTrue(PreviewResumePolicy.shouldRefresh(durationUs = 7_000_000L, playing = false))
        assertTrue(PreviewResumePolicy.shouldRefresh(durationUs = 7_000_000L, playing = true))
    }

    @Test
    fun contentReloadAfterExternalReturnRebindsTheBoundSurfaceWhenReady() {
        assertTrue(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = true,
                durationUs = 7_000_000L,
                hasBoundView = true,
            ),
        )
        assertTrue(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 7_000_000L,
                hasBoundView = true,
                surfaceReady = true,
            ),
        )
    }

    @Test
    fun finalPreviewReloadUsesTheSameReadySurfaceRecoveryContract() {
        assertTrue(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = true,
                durationUs = 12_000_000L,
                hasBoundView = true,
            ),
        )
        assertTrue(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 12_000_000L,
                hasBoundView = true,
                surfaceReady = true,
            ),
        )
        assertFalse(
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = true,
            ),
        )
    }

    @Test
    fun emptyPreviewMissingViewOrUnreadySurfaceDoesNotAttemptSurfaceRecovery() {
        assertFalse(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 0L,
                hasBoundView = true,
                surfaceReady = true,
            ),
        )
        assertFalse(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 7_000_000L,
                hasBoundView = false,
                surfaceReady = true,
            ),
        )
        assertFalse(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 7_000_000L,
                hasBoundView = true,
                surfaceReady = false,
            ),
        )
    }
}
