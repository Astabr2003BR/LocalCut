package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.PathPoint
import kotlin.math.max
import kotlin.math.min

data class VectorEraserCandidate(
    val elementId: String,
    val zIndex: Int,
    val points: List<PathPoint>,
)

object VectorEraserPolicy {
    const val MAX_CANDIDATES = 512
    const val MAX_RADIUS_NORMALIZED = 0.25

    fun findTopmostHit(
        candidates: List<VectorEraserCandidate>,
        eraserPoints: List<PathPoint>,
        radiusNormalized: Double,
    ): String? {
        require(radiusNormalized.isFinite() && radiusNormalized > 0.0 && radiusNormalized <= MAX_RADIUS_NORMALIZED) {
            "eraser radius must be finite and in (0, $MAX_RADIUS_NORMALIZED]"
        }
        require(eraserPoints.size >= 2) { "eraser gesture requires at least two points" }
        require(eraserPoints.all(::isFinitePoint)) { "eraser points must be finite" }

        return candidates.asSequence()
            .filter { it.elementId.isNotBlank() && it.points.size >= 2 && it.points.all(::isFinitePoint) }
            .sortedWith(compareByDescending<VectorEraserCandidate> { it.zIndex }.thenByDescending { it.elementId })
            .take(MAX_CANDIDATES)
            .firstOrNull { candidate -> pathsWithinRadius(candidate.points, eraserPoints, radiusNormalized) }
            ?.elementId
    }

    private fun pathsWithinRadius(
        path: List<PathPoint>,
        eraser: List<PathPoint>,
        radius: Double,
    ): Boolean {
        val radiusSquared = radius * radius
        eraser.zipWithNext().forEach { (eraserStart, eraserEnd) ->
            path.zipWithNext().forEach { (pathStart, pathEnd) ->
                if (segmentsIntersect(eraserStart, eraserEnd, pathStart, pathEnd)) return true
                if (pointSegmentDistanceSquared(eraserStart, pathStart, pathEnd) <= radiusSquared) return true
                if (pointSegmentDistanceSquared(eraserEnd, pathStart, pathEnd) <= radiusSquared) return true
                if (pointSegmentDistanceSquared(pathStart, eraserStart, eraserEnd) <= radiusSquared) return true
                if (pointSegmentDistanceSquared(pathEnd, eraserStart, eraserEnd) <= radiusSquared) return true
            }
        }
        return false
    }

    private fun pointSegmentDistanceSquared(point: PathPoint, start: PathPoint, end: PathPoint): Double {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return distanceSquared(point, start)
        val projection = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
        val t = projection.coerceIn(0.0, 1.0)
        val projected = PathPoint(start.x + t * dx, start.y + t * dy)
        return distanceSquared(point, projected)
    }

    private fun segmentsIntersect(a: PathPoint, b: PathPoint, c: PathPoint, d: PathPoint): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)
        if (o1 * o2 < 0.0 && o3 * o4 < 0.0) return true
        return (o1 == 0.0 && onSegment(a, c, b)) ||
            (o2 == 0.0 && onSegment(a, d, b)) ||
            (o3 == 0.0 && onSegment(c, a, d)) ||
            (o4 == 0.0 && onSegment(c, b, d))
    }

    private fun orientation(a: PathPoint, b: PathPoint, c: PathPoint): Double {
        val value = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
        return when {
            kotlin.math.abs(value) < 1e-12 -> 0.0
            value > 0.0 -> 1.0
            else -> -1.0
        }
    }

    private fun onSegment(a: PathPoint, p: PathPoint, b: PathPoint): Boolean =
        p.x >= min(a.x, b.x) - 1e-12 && p.x <= max(a.x, b.x) + 1e-12 &&
            p.y >= min(a.y, b.y) - 1e-12 && p.y <= max(a.y, b.y) + 1e-12

    private fun distanceSquared(left: PathPoint, right: PathPoint): Double {
        val dx = left.x - right.x
        val dy = left.y - right.y
        return dx * dx + dy * dy
    }

    private fun isFinitePoint(point: PathPoint): Boolean = point.x.isFinite() && point.y.isFinite()
}
