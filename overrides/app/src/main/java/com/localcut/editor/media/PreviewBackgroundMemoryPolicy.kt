package com.localcut.editor.media

object PreviewBackgroundMemoryPolicy {
    fun shouldSuspend(
        previewDurationUs: Long,
        changingConfigurations: Boolean,
    ): Boolean = previewDurationUs > 0L && !changingConfigurations
}
