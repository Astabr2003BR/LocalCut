package com.localcut.editor.ui.editor

object TimelineGesturePolicy {
    fun isHorizontalScrollEnabled(exclusiveHandleActive: Boolean): Boolean =
        !exclusiveHandleActive
}
