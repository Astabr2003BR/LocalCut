package com.localcut.editor.ui.editor

object DrawStrokePadPolicy {
    const val DEFAULT_DURATION_US = 3_000_000L
    const val DEFAULT_STROKE_WIDTH_PX = 12.0

    fun nextStrokeId(existingIds: Set<String>): String {
        var index = 1
        while ("stroke-$index" in existingIds) index += 1
        return "stroke-$index"
    }

    fun nextZIndex(existing: List<Int>): Int {
        val max = existing.maxOrNull() ?: -1
        require(max < Int.MAX_VALUE) { "cannot place stroke above max z-index" }
        return max + 1
    }
}
