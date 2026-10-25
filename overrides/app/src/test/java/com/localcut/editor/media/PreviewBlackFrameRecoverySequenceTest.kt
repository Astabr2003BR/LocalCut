package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBlackFrameRecoverySequenceTest {
    @Test
    fun externalPickerReturnKeepsPlayerAndRebindsVideoOutput() {
        assertTrue(PreviewPlayerViewPolicy.ENABLE_COMPOSE_SURFACE_SYNC_WORKAROUND)
        assertTrue(PreviewPlayerViewPolicy.KEEP_CONTENT_ON_PLAYER_RESET)

        // Keep the CompositionPlayer instance stable, but explicitly re-register the
        // current PlayerView video output after an external activity may recreate its surface.
        assertFalse(
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = true,
            ),
        )
        assertTrue(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 7_000_000L,
                hasBoundView = true,
            ),
        )

        assertTrue(PreviewResumePolicy.shouldRefresh(durationUs = 7_000_000L, playing = false))
        assertTrue(PreviewResumePolicy.shouldRefresh(durationUs = 7_000_000L, playing = true))
    }

    @Test
    fun contentReloadAfterExternalReturnRebindsTheBoundSurface() {
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
            ),
        )
    }

    @Test
    fun finalPreviewReloadUsesTheSameVideoOutputRecoveryContract() {
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
            ),
        )
        assertFalse(
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = true,
            ),
        )
    }

    @Test
    fun emptyPreviewOrMissingViewDoesNotAttemptSurfaceRecovery() {
        assertFalse(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 0L,
                hasBoundView = true,
            ),
        )
        assertFalse(
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = 7_000_000L,
                hasBoundView = false,
            ),
        )
    }
}
