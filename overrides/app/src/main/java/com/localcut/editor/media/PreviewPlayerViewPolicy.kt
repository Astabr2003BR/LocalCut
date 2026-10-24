package com.localcut.editor.media

object PreviewPlayerViewPolicy {
    const val ENABLE_COMPOSE_SURFACE_SYNC_WORKAROUND = true
    const val KEEP_CONTENT_ON_PLAYER_RESET = true

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
}
