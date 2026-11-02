package com.localcut.editor.domain.design

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

enum class LineAngleSnapMode(val incrementDeg: Double?) {
    OFF(null),
    DEG_15(15.0),
    DEG_45(45.0),
}

object LineAngleSnapPolicy {
    fun apply(
        points: List<CanvasPointPx>,
        mode: LineAngleSnapMode,
    ): List<CanvasPointPx> {
        require(points.size >= 2) { "line snap requires at least two points" }
        require(points.all { it.x.isFinite() && it.y.isFinite() }) { "line points must be finite" }
        if (mode == LineAngleSnapMode.OFF) return points.toList()

        val start = points.first()
        val end = points.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy)
        if (length == 0.0) return listOf(start, end)

        val incrementRad = requireNotNull(mode.incrementDeg) * PI / 180.0
        val angle = atan2(dy, dx)
        val snappedAngle = round(angle / incrementRad) * incrementRad
        return listOf(
            start,
            CanvasPointPx(
                x = start.x + cos(snappedAngle) * length,
                y = start.y + sin(snappedAngle) * length,
            ),
        )
    }
}
