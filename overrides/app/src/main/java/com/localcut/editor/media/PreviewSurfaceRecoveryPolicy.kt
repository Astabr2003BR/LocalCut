package com.localcut.editor.media

object PreviewSurfaceRecoveryPolicy {
    fun shouldRebindVideoOutput(
        durationUs: Long,
        hasBoundView: Boolean,
    ): Boolean = durationUs > 0L && hasBoundView
}
