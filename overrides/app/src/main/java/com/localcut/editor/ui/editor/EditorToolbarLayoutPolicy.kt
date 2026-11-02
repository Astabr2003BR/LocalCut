package com.localcut.editor.ui.editor

object EditorToolbarLayoutPolicy {
    private const val COMPACT_BREAKPOINT_DP = 430f
    const val COMPACT_MIN_INTERACTIVE_SIZE_DP = 44f
    const val REGULAR_MIN_INTERACTIVE_SIZE_DP = 48f

    fun useCompactLayout(widthDp: Float): Boolean =
        widthDp.isFinite() && widthDp > 0f && widthDp < COMPACT_BREAKPOINT_DP

    fun minimumInteractiveSizeDp(widthDp: Float): Float =
        if (useCompactLayout(widthDp)) COMPACT_MIN_INTERACTIVE_SIZE_DP
        else REGULAR_MIN_INTERACTIVE_SIZE_DP
}
