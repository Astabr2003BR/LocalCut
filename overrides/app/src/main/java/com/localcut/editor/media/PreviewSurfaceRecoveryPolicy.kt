package com.localcut.editor.media

object PreviewSurfaceRecoveryPolicy {
    fun shouldRebindVideoOutput(
        durationUs: Long,
        hasBoundView: Boolean,
        surfaceReady: Boolean,
    ): Boolean = durationUs > 0L && hasBoundView && surfaceReady
}
