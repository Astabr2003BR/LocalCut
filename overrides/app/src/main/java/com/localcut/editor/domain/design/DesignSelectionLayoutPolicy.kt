package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.ShapeKind

data class DesignPathBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val centerX: Double get() = (minX + maxX) / 2.0
    val centerY: Double get() = (minY + maxY) / 2.0
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
}

object DesignSelectionLayoutPolicy {
    fun alignLeft(elements: List<DesignElement>, canvasWidthPx: Double, canvasHeightPx: Double): List<DesignElement> =
        align(elements, canvasWidthPx, canvasHeightPx) { selection, bounds -> selection.minX - bounds.minX to 0.0 }

    fun alignCenterX(elements: List<DesignElement>, canvasWidthPx: Double, canvasHeightPx: Double): List<DesignElement> =
        align(elements, canvasWidthPx, canvasHeightPx) { selection, bounds -> selection.centerX - bounds.centerX to 0.0 }

    fun alignRight(elements: List<DesignElement>, canvasWidthPx: Double, canvasHeightPx: Double): List<DesignElement> =
        align(elements, canvasWidthPx, canvasHeightPx) { selection, bounds -> selection.maxX - bounds.maxX to 0.0 }

    fun alignTop(elements: List<DesignElement>, canvasWidthPx: Double, canvasHeightPx: Double): List<DesignElement> =
        align(elements, canvasWidthPx, canvasHeightPx) { selection, bounds -> 0.0 to (selection.minY - bounds.minY) }

    fun alignCenterY(elements: List<DesignElement>, canvasWidthPx: Double, canvasHeightPx: Double): List<DesignElement> =
        align(elements, canvasWidthPx, canvasHeightPx) { selection, bounds -> 0.0 to (selection.centerY - bounds.centerY) }

    fun alignBottom(elements: List<DesignElement>, canvasWidthPx: Double, canvasHeightPx: Double): List<DesignElement> =
        align(elements, canvasWidthPx, canvasHeightPx) { selection, bounds -> 0.0 to (selection.maxY - bounds.maxY) }

    fun distributeHorizontally(
        elements: List<DesignElement>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
    ): List<DesignElement> = distribute(elements, canvasWidthPx, canvasHeightPx, horizontal = true)

    fun distributeVertically(
        elements: List<DesignElement>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
    ): List<DesignElement> = distribute(elements, canvasWidthPx, canvasHeightPx, horizontal = false)

    fun boundsFor(
        element: DesignElement,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
    ): DesignPathBounds {
        validateCanvas(canvasWidthPx, canvasHeightPx)
        require(element.shape?.kind == ShapeKind.PATH && element.shape?.path != null) {
            "design layout currently requires PATH elements"
        }
        val points = DesignElementGeometryPolicy.transformedPathPointsPx(element, canvasWidthPx, canvasHeightPx)
        require(points.isNotEmpty()) { "design layout path geometry must not be empty" }
        return DesignPathBounds(
            minX = points.minOf { it.x },
            minY = points.minOf { it.y },
            maxX = points.maxOf { it.x },
            maxY = points.maxOf { it.y },
        )
    }

    private fun align(
        elements: List<DesignElement>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
        delta: (DesignPathBounds, DesignPathBounds) -> Pair<Double, Double>,
    ): List<DesignElement> {
        val measured = measured(elements, canvasWidthPx, canvasHeightPx, minCount = 2)
        val selection = union(measured.map { it.second })
        return measured.map { (element, bounds) ->
            val (dx, dy) = delta(selection, bounds)
            shift(element, dx, dy)
        }
    }

    private fun distribute(
        elements: List<DesignElement>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
        horizontal: Boolean,
    ): List<DesignElement> {
        val measured = measured(elements, canvasWidthPx, canvasHeightPx, minCount = 3)
        val sorted = if (horizontal) {
            measured.sortedWith(compareBy<Pair<DesignElement, DesignPathBounds>> { it.second.minX }.thenBy { it.first.id })
        } else {
            measured.sortedWith(compareBy<Pair<DesignElement, DesignPathBounds>> { it.second.minY }.thenBy { it.first.id })
        }
        val first = sorted.first().second
        val last = sorted.last().second
        val totalExtent = sorted.sumOf { if (horizontal) it.second.width else it.second.height }
        val outerExtent = if (horizontal) last.maxX - first.minX else last.maxY - first.minY
        val gap = (outerExtent - totalExtent) / (sorted.size - 1).toDouble()
        require(gap.isFinite()) { "design distribution gap must be finite" }

        var cursor = if (horizontal) first.minX else first.minY
        val shifted = linkedMapOf<String, DesignElement>()
        sorted.forEach { (element, bounds) ->
            val currentStart = if (horizontal) bounds.minX else bounds.minY
            val offset = cursor - currentStart
            shifted[element.id] = if (horizontal) shift(element, offset, 0.0) else shift(element, 0.0, offset)
            cursor += (if (horizontal) bounds.width else bounds.height) + gap
        }
        return elements.map { shifted.getValue(it.id) }
    }

    private fun measured(
        elements: List<DesignElement>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
        minCount: Int,
    ): List<Pair<DesignElement, DesignPathBounds>> {
        validateCanvas(canvasWidthPx, canvasHeightPx)
        require(elements.size >= minCount) { "design layout requires at least $minCount elements" }
        require(elements.map { it.id }.distinct().size == elements.size) { "design layout element ids must be unique" }
        return elements.map { it to boundsFor(it, canvasWidthPx, canvasHeightPx) }
    }

    private fun union(bounds: List<DesignPathBounds>): DesignPathBounds = DesignPathBounds(
        minX = bounds.minOf { it.minX },
        minY = bounds.minOf { it.minY },
        maxX = bounds.maxOf { it.maxX },
        maxY = bounds.maxOf { it.maxY },
    )

    private fun shift(element: DesignElement, dx: Double, dy: Double): DesignElement {
        require(dx.isFinite() && dy.isFinite()) { "design layout shift must be finite" }
        return element.copy(
            transform = element.transform.copy(
                x = element.transform.x + dx,
                y = element.transform.y + dy,
            ),
        )
    }

    private fun validateCanvas(width: Double, height: Double) {
        require(width.isFinite() && width > 0.0) { "canvas width must be finite and positive" }
        require(height.isFinite() && height > 0.0) { "canvas height must be finite and positive" }
    }
}
