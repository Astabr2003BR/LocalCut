package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.VectorPath
import kotlin.math.roundToInt

data class CanvasPointPx(val x: Double, val y: Double)

object DrawStrokePolicy {
    const val MAX_POINTS = 256
    private const val MIN_NORMALIZED_DISTANCE_SQUARED = 0.000001

    fun normalize(
        points: List<CanvasPointPx>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
    ): List<PathPoint> {
        require(canvasWidthPx.isFinite() && canvasWidthPx > 0.0) { "canvas width must be finite and positive" }
        require(canvasHeightPx.isFinite() && canvasHeightPx > 0.0) { "canvas height must be finite and positive" }
        require(points.size >= 2) { "stroke requires at least two points" }
        require(points.all { it.x.isFinite() && it.y.isFinite() }) { "stroke points must be finite" }

        val normalized = points.map { point ->
            PathPoint(
                x = (point.x / canvasWidthPx).coerceIn(0.0, 1.0),
                y = (point.y / canvasHeightPx).coerceIn(0.0, 1.0),
            )
        }
        val deduplicated = buildList<PathPoint> {
            normalized.forEach { point ->
                val previous = lastOrNull()
                if (previous == null || distanceSquared(previous, point) >= MIN_NORMALIZED_DISTANCE_SQUARED) add(point)
            }
        }
        require(deduplicated.size >= 2) { "stroke collapses to fewer than two distinct points" }
        if (deduplicated.size <= MAX_POINTS) return deduplicated

        return (0 until MAX_POINTS).map { index ->
            val sourceIndex = (index.toDouble() * (deduplicated.lastIndex.toDouble() / (MAX_POINTS - 1))).roundToInt()
            deduplicated[sourceIndex.coerceIn(0, deduplicated.lastIndex)]
        }
    }

    fun createPathElement(
        id: String,
        startUs: Long,
        durationUs: Long,
        points: List<CanvasPointPx>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
        strokeArgb: Int,
        strokeWidthPx: Double,
        zIndex: Int = 0,
    ): DesignElement {
        require(id.isNotBlank()) { "stroke id must not be blank" }
        require(startUs >= 0L) { "stroke start must be non-negative" }
        require(durationUs > 0L) { "stroke duration must be positive" }
        require(strokeWidthPx.isFinite() && strokeWidthPx in 0.5..256.0) { "stroke width must be in 0.5..256 px" }
        return DesignElement(
            id = id,
            startUs = startUs,
            durationUs = durationUs,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(
                    points = normalize(points, canvasWidthPx, canvasHeightPx),
                    closed = false,
                ),
                fillArgb = null,
                strokeArgb = strokeArgb,
                strokeWidthPx = strokeWidthPx,
            ),
            zIndex = zIndex,
        )
    }

    private fun distanceSquared(left: PathPoint, right: PathPoint): Double {
        val dx = left.x - right.x
        val dy = left.y - right.y
        return dx * dx + dy * dy
    }
}
