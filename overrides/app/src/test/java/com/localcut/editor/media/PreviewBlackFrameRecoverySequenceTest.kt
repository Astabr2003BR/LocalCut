package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBlackFrameRecoverySequenceTest {
    @Test
    fun externalPickerReturnKeepsStableSurfaceAndRequestsFrameRefresh() {
        assertTrue(PreviewPlayerViewPolicy.ENABLE_COMPOSE_SURFACE_SYNC_WORKAROUND)
        assertTrue(PreviewPlayerViewPolicy.KEEP_CONTENT_ON_PLAYER_RESET)

        // Returning from an Android picker must keep the already-bound CompositionPlayer.
        // Reassigning the same player is the sequence that previously produced a black
        // SurfaceView while playback time continued advancing.
        assertFalse(
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = true,
            ),
        )

        // The resumed editor must still refresh the current frame whether playback was
        // paused by the external activity or was already playing.
        assertTrue(PreviewResumePolicy.shouldRefresh(durationUs = 7_000_000L, playing = false))
        assertTrue(PreviewResumePolicy.shouldRefresh(durationUs = 7_000_000L, playing = true))
    }

    @Test
    fun contentReloadAfterExternalReturnRefreshesTheBoundSurface() {
        assertTrue(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = true,
                durationUs = 7_000_000L,
                hasBoundView = true,
            ),
        )
    }

    @Test
    fun finalPreviewReloadUsesTheSameBoundSurfaceRecoveryContract() {
        assertTrue(
            PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = true,
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
}
