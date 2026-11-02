package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.ShapeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object DesignElementGeometryPolicy {
    const val BASE_SIZE_FRACTION_OF_SHORT_EDGE = 0.25

    fun baseSizePx(canvasWidthPx: Double, canvasHeightPx: Double): Double {
        require(canvasWidthPx.isFinite() && canvasWidthPx > 0.0) { "canvas width must be finite and positive" }
        require(canvasHeightPx.isFinite() && canvasHeightPx > 0.0) { "canvas height must be finite and positive" }
        return min(canvasWidthPx, canvasHeightPx) * BASE_SIZE_FRACTION_OF_SHORT_EDGE
    }

    fun transformedPathPointsPx(
        element: DesignElement,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
    ): List<CanvasPointPx> {
        val shape = requireNotNull(element.shape) { "design element requires shape" }
        require(shape.kind == ShapeKind.PATH) { "design element must be PATH" }
        val path = requireNotNull(shape.path) { "path shape requires vector path" }
        require(path.points.all { it.x.isFinite() && it.y.isFinite() }) { "path points must be finite" }
        val transform = element.transform
        require(
            transform.x.isFinite() &&
                transform.y.isFinite() &&
                transform.scale.isFinite() &&
                transform.scale > 0.0 &&
                transform.rotationDeg.isFinite()
        ) { "design transform must be finite with positive scale" }

        val sizePx = baseSizePx(canvasWidthPx, canvasHeightPx) * transform.scale
        val centerX = canvasWidthPx / 2.0 + transform.x
        val centerY = canvasHeightPx / 2.0 + transform.y
        val radians = transform.rotationDeg * PI / 180.0
        val cosine = cos(radians)
        val sine = sin(radians)

        return path.points.map { point ->
            val localX = (point.x - 0.5) * sizePx
            val localY = (point.y - 0.5) * sizePx
            CanvasPointPx(
                x = centerX + localX * cosine - localY * sine,
                y = centerY + localX * sine + localY * cosine,
            )
        }
    }
}

object VectorLassoSelectionPolicy {
    private const val EPSILON = 1e-9

    fun selectPathIds(
        elements: List<DesignElement>,
        lassoPointsPx: List<CanvasPointPx>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
        playheadUs: Long,
    ): List<String> {
        DesignElementGeometryPolicy.baseSizePx(canvasWidthPx, canvasHeightPx)
        require(playheadUs >= 0L) { "lasso playhead must be non-negative" }
        require(lassoPointsPx.size >= 3) { "lasso requires at least three points" }
        require(lassoPointsPx.all { it.x.isFinite() && it.y.isFinite() }) { "lasso points must be finite" }
        require(hasArea(lassoPointsPx)) { "lasso polygon must have positive area" }

        return elements.asSequence()
            .filter { element ->
                val shape = element.shape
                shape?.kind == ShapeKind.PATH && shape.path != null && isActiveAt(element, playheadUs)
            }
            .filter { element ->
                val pathPoints = DesignElementGeometryPolicy.transformedPathPointsPx(
                    element,
                    canvasWidthPx,
                    canvasHeightPx,
                )
                pathIntersectsPolygon(pathPoints, lassoPointsPx)
            }
            .sortedWith(compareBy<DesignElement> { it.zIndex }.thenBy { it.id })
            .map { it.id }
            .toList()
    }

    private fun isActiveAt(element: DesignElement, playheadUs: Long): Boolean {
        if (playheadUs < element.startUs) return false
        val endUs = try {
            Math.addExact(element.startUs, element.durationUs)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        return playheadUs < endUs
    }

    private fun pathIntersectsPolygon(
        path: List<CanvasPointPx>,
        polygon: List<CanvasPointPx>,
    ): Boolean {
        if (path.isEmpty()) return false
        if (path.any { pointInPolygonOrBoundary(it, polygon) }) return true
        if (path.size < 2) return false

        val polygonEdges = polygon.indices.map { index ->
            polygon[index] to polygon[(index + 1) % polygon.size]
        }
        return path.zipWithNext().any { (left, right) ->
            polygonEdges.any { (edgeStart, edgeEnd) ->
                segmentsIntersect(left, right, edgeStart, edgeEnd)
            }
        }
    }

    private fun pointInPolygonOrBoundary(point: CanvasPointPx, polygon: List<CanvasPointPx>): Boolean {
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            if (pointOnSegment(point, previous, current)) return true
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val xAtY = (previous.x - current.x) * (point.y - current.y) /
                    (previous.y - current.y) + current.x
                if (point.x < xAtY) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun segmentsIntersect(
        a: CanvasPointPx,
        b: CanvasPointPx,
        c: CanvasPointPx,
        d: CanvasPointPx,
    ): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)

        if (o1 * o2 < -EPSILON && o3 * o4 < -EPSILON) return true
        return (abs(o1) <= EPSILON && pointOnSegment(c, a, b)) ||
            (abs(o2) <= EPSILON && pointOnSegment(d, a, b)) ||
            (abs(o3) <= EPSILON && pointOnSegment(a, c, d)) ||
            (abs(o4) <= EPSILON && pointOnSegment(b, c, d))
    }

    private fun orientation(a: CanvasPointPx, b: CanvasPointPx, c: CanvasPointPx): Double =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun pointOnSegment(point: CanvasPointPx, a: CanvasPointPx, b: CanvasPointPx): Boolean {
        if (abs(orientation(a, b, point)) > EPSILON) return false
        return point.x >= minOf(a.x, b.x) - EPSILON &&
            point.x <= maxOf(a.x, b.x) + EPSILON &&
            point.y >= minOf(a.y, b.y) - EPSILON &&
            point.y <= maxOf(a.y, b.y) + EPSILON
    }

    private fun hasArea(points: List<CanvasPointPx>): Boolean {
        var twiceArea = 0.0
        points.indices.forEach { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += current.x * next.y - next.x * current.y
        }
        return abs(twiceArea) > EPSILON
    }
}
