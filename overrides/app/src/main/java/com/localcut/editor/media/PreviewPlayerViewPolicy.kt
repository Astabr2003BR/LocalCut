package com.localcut.editor.media

import androidx.media3.ui.AspectRatioFrameLayout

object PreviewPlayerViewPolicy {
    const val ENABLE_COMPOSE_SURFACE_SYNC_WORKAROUND = true
    const val KEEP_CONTENT_ON_PLAYER_RESET = true
    const val RESIZE_MODE = AspectRatioFrameLayout.RESIZE_MODE_FIT

    fun shouldRefreshAfterViewportResize(
        previousHeightDp: Float,
        currentHeightDp: Float,
        durationUs: Long,
    ): Boolean =
        durationUs > 0L &&
            previousHeightDp.isFinite() &&
            currentHeightDp.isFinite() &&
            previousHeightDp != currentHeightDp

    fun shouldRefreshAfterContentLoad(
        loadSucceeded: Boolean,
        durationUs: Long,
        hasBoundView: Boolean,
    ): Boolean = loadSucceeded && durationUs > 0L && hasBoundView

    fun shouldAssignPreviewPlayer(alreadyBoundToPreviewPlayer: Boolean): Boolean =
        !alreadyBoundToPreviewPlayer
}
