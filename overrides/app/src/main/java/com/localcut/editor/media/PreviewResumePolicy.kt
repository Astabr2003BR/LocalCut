package com.localcut.editor.media

object PreviewResumePolicy {
    fun shouldRefresh(durationUs: Long, playing: Boolean): Boolean = durationUs > 0L

    fun shouldRestorePosition(durationUs: Long): Boolean = durationUs > 0L
}
