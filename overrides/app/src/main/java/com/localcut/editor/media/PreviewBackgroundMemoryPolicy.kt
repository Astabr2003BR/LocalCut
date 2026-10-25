package com.localcut.editor.media

object PreviewBackgroundMemoryPolicy {
    fun shouldSuspend(
        previewDurationUs: Long,
        changingConfigurations: Boolean,
    ): Boolean = previewDurationUs > 0L && !changingConfigurations
}

/** Keeps the editor preview's background/resume handoff idempotent. */
class PreviewBackgroundSession {
    private var savedPositionUs: Long? = null

    val isSuspended: Boolean
        get() = savedPositionUs != null

    fun beginSuspend(positionUs: Long): Boolean {
        if (savedPositionUs != null) return false
        savedPositionUs = positionUs.coerceAtLeast(0L)
        return true
    }

    fun takeResumePositionUs(): Long? {
        val saved = savedPositionUs ?: return null
        savedPositionUs = null
        return saved
    }
}
