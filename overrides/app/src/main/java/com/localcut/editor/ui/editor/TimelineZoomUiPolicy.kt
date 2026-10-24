package com.localcut.editor.ui.editor

object TimelineZoomUiPolicy {
    const val MIN_DP_PER_SECOND = 40f
    const val DEFAULT_DP_PER_SECOND = 84f
    const val MAX_DP_PER_SECOND = 240f
    const val STEP_DP_PER_SECOND = 20f

    fun clamp(value: Float): Float = value.coerceIn(MIN_DP_PER_SECOND, MAX_DP_PER_SECOND)

    fun zoomIn(value: Float): Float = clamp(value + STEP_DP_PER_SECOND)

    fun zoomOut(value: Float): Float = clamp(value - STEP_DP_PER_SECOND)

    fun reset(): Float = DEFAULT_DP_PER_SECOND

    fun percent(value: Float): Int =
        ((clamp(value) / DEFAULT_DP_PER_SECOND) * 100f).toInt()
}
