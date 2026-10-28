package com.localcut.editor.domain.design

enum class StrokeSymmetryMode {
    NONE,
    VERTICAL_AXIS,
    HORIZONTAL_AXIS,
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
        val mirrored = when (mode) {
            StrokeSymmetryMode.NONE -> return listOf(original)
            StrokeSymmetryMode.VERTICAL_AXIS -> original.map { point ->
                CanvasPointPx(
                    x = canvasWidthPx - point.x,
                    y = point.y,
                )
            }
            StrokeSymmetryMode.HORIZONTAL_AXIS -> original.map { point ->
                CanvasPointPx(
                    x = point.x,
                    y = canvasHeightPx - point.y,
                )
            }
        }
        return listOf(original, mirrored)
    }
}
