package com.localcut.editor.ui.editor

import com.localcut.editor.domain.design.CanvasPointPx
import kotlin.math.roundToInt

object VectorLassoUiPolicy {
    const val MAX_POINTS = 128

    fun toProjectPolygon(
        points: List<CanvasPointPx>,
        surfaceWidthPx: Double,
        surfaceHeightPx: Double,
        projectWidthPx: Double,
        projectHeightPx: Double,
    ): List<CanvasPointPx> {
        require(surfaceWidthPx.isFinite() && surfaceWidthPx > 0.0) { "lasso surface width must be positive" }
        require(surfaceHeightPx.isFinite() && surfaceHeightPx > 0.0) { "lasso surface height must be positive" }
        require(projectWidthPx.isFinite() && projectWidthPx > 0.0) { "lasso project width must be positive" }
        require(projectHeightPx.isFinite() && projectHeightPx > 0.0) { "lasso project height must be positive" }
        require(points.size >= 3) { "lasso requires at least three points" }
        require(points.all { it.x.isFinite() && it.y.isFinite() }) { "lasso points must be finite" }

        val bounded = if (points.size <= MAX_POINTS) {
            points
        } else {
            (0 until MAX_POINTS).map { index ->
                val sourceIndex = (index.toDouble() * points.lastIndex / (MAX_POINTS - 1).toDouble())
                    .roundToInt()
                    .coerceIn(0, points.lastIndex)
                points[sourceIndex]
            }
        }

        return bounded.map { point ->
            CanvasPointPx(
                x = (point.x / surfaceWidthPx).coerceIn(0.0, 1.0) * projectWidthPx,
                y = (point.y / surfaceHeightPx).coerceIn(0.0, 1.0) * projectHeightPx,
            )
        }
    }
}
