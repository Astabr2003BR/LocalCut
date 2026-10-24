package com.localcut.editor.media

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
}
