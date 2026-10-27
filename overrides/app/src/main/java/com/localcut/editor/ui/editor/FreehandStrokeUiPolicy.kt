package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.VectorPath
import kotlin.math.max
import kotlin.math.min

object FreehandStrokeUiPolicy {
    const val MAX_POINTS = 256
    const val MIN_BRUSH_WIDTH_PX = 1.0
    const val MAX_BRUSH_WIDTH_PX = 64.0
    const val MAX_STORED_STROKE_WIDTH_PX = 200.0
    const val DEFAULT_DURATION_US = 3_000_000L
    private const val BASE_SIZE_FRACTION = 0.25

    fun createStroke(
        projectWidth: Int,
        projectHeight: Int,
        existingIds: Set<String>,
        maxZIndex: Int?,
        playheadUs: Long,
        points: List<PathPoint>,
        colorArgb: Int,
        brushWidthPx: Double,
    ): DesignElement {
        require(projectWidth > 0 && projectHeight > 0) { "project dimensions must be positive" }
        require(playheadUs >= 0L) { "playheadUs must be non-negative" }
        require(brushWidthPx.isFinite() && brushWidthPx in MIN_BRUSH_WIDTH_PX..MAX_BRUSH_WIDTH_PX) {
            "brush width out of range"
        }
        require(points.size >= 2) { "freehand stroke requires at least two points" }
        points.forEach { point ->
            require(point.x.isFinite() && point.y.isFinite() && point.x in 0.0..1.0 && point.y in 0.0..1.0) {
                "stroke points must be finite and normalized"
            }
        }

        val sampled = sample(points, MAX_POINTS)
        val projectPoints = sampled.map { point ->
            PathPoint(point.x * projectWidth.toDouble(), point.y * projectHeight.toDouble())
        }
        val minX = projectPoints.minOf { it.x }
        val maxX = projectPoints.maxOf { it.x }
        val minY = projectPoints.minOf { it.y }
        val maxY = projectPoints.maxOf { it.y }
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        val baseSize = min(projectWidth, projectHeight).toDouble() * BASE_SIZE_FRACTION
        val contentExtent = max(maxX - minX, maxY - minY)
        val minimumScaleForStroke = brushWidthPx / MAX_STORED_STROKE_WIDTH_PX
        val scale = max(max(contentExtent / baseSize, minimumScaleForStroke), 0.01)
        val squareSize = baseSize * scale
        val localPoints = projectPoints.map { point ->
            PathPoint(
                x = 0.5 + (point.x - centerX) / squareSize,
                y = 0.5 + (point.y - centerY) / squareSize,
            )
        }
        val nextZ = when (maxZIndex) {
            null -> 0
            Int.MAX_VALUE -> throw IllegalArgumentException("cannot add stroke above max z-index")
            else -> maxZIndex + 1
        }
        val element = DesignElement(
            id = nextId(existingIds),
            startUs = playheadUs,
            durationUs = DEFAULT_DURATION_US,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(points = localPoints, closed = false),
                fillArgb = null,
                strokeArgb = colorArgb,
                strokeWidthPx = brushWidthPx / scale,
            ),
            transform = TransformValue(
                x = centerX - projectWidth / 2.0,
                y = centerY - projectHeight / 2.0,
                scale = scale,
            ),
            zIndex = nextZ,
        )
        val issues = element.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        return element
    }

    fun isFreehandStroke(element: DesignElement): Boolean =
        element.id.startsWith("stroke-") &&
            element.shape?.kind == ShapeKind.PATH &&
            element.shape.path?.closed == false

    private fun sample(points: List<PathPoint>, maxPoints: Int): List<PathPoint> {
        if (points.size <= maxPoints) return points
        val lastIndex = points.lastIndex
        return (0 until maxPoints).map { index ->
            val sourceIndex = ((index.toLong() * lastIndex) / (maxPoints - 1L)).toInt()
            points[sourceIndex]
        }
    }

    private fun nextId(existingIds: Set<String>): String {
        var index = 1L
        while (true) {
            val candidate = "stroke-$index"
            if (candidate !in existingIds) return candidate
            check(index < Long.MAX_VALUE) { "unable to allocate stroke id" }
            index++
        }
    }
}
