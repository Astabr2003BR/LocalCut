package com.localcut.editor.ui.editor

object TimelineTrackLockUiPolicy {
    fun nextLocked(currentLocked: Boolean): Boolean = !currentLocked

    fun label(locked: Boolean): String = if (locked) "L✓" else "L"
}
