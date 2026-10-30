package com.localcut.editor.ui.editor

import kotlin.math.max
import kotlin.math.min

object ObjectTrackingSelectionGesturePolicy {
    private const val MIN_DRAG_PX = 1.0

    fun rect(
        startX: Double,
        startY: Double,
        currentX: Double,
        currentY: Double,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
    ): PreviewSelectionRect? {
        require(viewportWidthPx.isFinite() && viewportWidthPx > 0.0) { "OBJECT_TRACKING_VIEWPORT_INVALID" }
        require(viewportHeightPx.isFinite() && viewportHeightPx > 0.0) { "OBJECT_TRACKING_VIEWPORT_INVALID" }
        require(listOf(startX, startY, currentX, currentY).all { it.isFinite() }) {
            "OBJECT_TRACKING_POINTER_NON_FINITE"
        }
        val sx = startX.coerceIn(0.0, viewportWidthPx)
        val sy = startY.coerceIn(0.0, viewportHeightPx)
        val cx = currentX.coerceIn(0.0, viewportWidthPx)
        val cy = currentY.coerceIn(0.0, viewportHeightPx)
        val left = min(sx, cx)
        val top = min(sy, cy)
        val right = max(sx, cx)
        val bottom = max(sy, cy)
        if (right - left < MIN_DRAG_PX || bottom - top < MIN_DRAG_PX) return null
        return PreviewSelectionRect(left, top, right, bottom)
    }
}
