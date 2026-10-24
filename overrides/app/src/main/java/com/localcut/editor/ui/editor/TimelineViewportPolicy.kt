package com.localcut.editor.ui.editor

import kotlin.math.roundToInt

object TimelineViewportPolicy {
    fun targetScrollPx(
        playheadPx: Float,
        currentScrollPx: Int,
        viewportWidthPx: Int,
        maxScrollPx: Int,
        edgePaddingPx: Int,
    ): Int {
        val safeMaxScroll = maxScrollPx.coerceAtLeast(0)
        val safeCurrentScroll = currentScrollPx.coerceIn(0, safeMaxScroll)
        if (!playheadPx.isFinite()) return safeCurrentScroll

        val safeViewportWidth = viewportWidthPx.coerceAtLeast(1)
        val safePadding = edgePaddingPx.coerceIn(0, safeViewportWidth / 2)
        val safeLeft = safeCurrentScroll + safePadding
        val safeRight = safeCurrentScroll + safeViewportWidth - safePadding

        val requested = when {
            playheadPx < safeLeft -> playheadPx - safePadding
            playheadPx > safeRight -> playheadPx - safeViewportWidth + safePadding
            else -> safeCurrentScroll.toFloat()
        }

        return requested
            .roundToInt()
            .coerceIn(0, safeMaxScroll)
    }
}
