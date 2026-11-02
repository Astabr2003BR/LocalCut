package com.localcut.editor.domain.design

enum class StrokeSymmetryMode {
    NONE,
    VERTICAL_AXIS,
    HORIZONTAL_AXIS,
    BOTH_AXES,
}

object StrokeSymmetryPolicy {
    fun expand(
        points: List<CanvasPointPx>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
        mode: StrokeSymmetryMode,
    ): List<List<CanvasPointPx>> {
        require(canvasWidthPx.isFinite() && canvasWidthPx > 0.0) { "canvas width must be finite and positive" }
        require(canvasHeightPx.isFinite() && canvasHeightPx > 0.0) { "canvas height must be finite and positive" }
        require(points.all { it.x.isFinite() && it.y.isFinite() }) { "stroke points must be finite" }
        require(points.size >= 2) { "symmetry requires at least two points" }

        val original = points.toList()
        if (mode == StrokeSymmetryMode.NONE) return listOf(original)

        val vertical = original.map { point ->
            CanvasPointPx(
                x = canvasWidthPx - point.x,
                y = point.y,
            )
        }
        val horizontal = original.map { point ->
            CanvasPointPx(
                x = point.x,
                y = canvasHeightPx - point.y,
            )
        }
        val both = original.map { point ->
            CanvasPointPx(
                x = canvasWidthPx - point.x,
                y = canvasHeightPx - point.y,
            )
        }

        val expanded = when (mode) {
            StrokeSymmetryMode.NONE -> listOf(original)
            StrokeSymmetryMode.VERTICAL_AXIS -> listOf(original, vertical)
            StrokeSymmetryMode.HORIZONTAL_AXIS -> listOf(original, horizontal)
            StrokeSymmetryMode.BOTH_AXES -> listOf(original, vertical, horizontal, both)
        }
        return expanded.distinct()
    }
}
