package com.localcut.editor.domain.design

enum class StrokeAssistMode {
    FREEHAND,
    LINE,
}

object StrokeAssistPolicy {
    fun apply(
        points: List<CanvasPointPx>,
        mode: StrokeAssistMode,
    ): List<CanvasPointPx> {
        require(points.all { it.x.isFinite() && it.y.isFinite() }) { "stroke points must be finite" }
        return when (mode) {
            StrokeAssistMode.FREEHAND -> points.toList()
            StrokeAssistMode.LINE -> {
                require(points.size >= 2) { "line assist requires at least two points" }
                listOf(points.first(), points.last())
            }
        }
    }
}
