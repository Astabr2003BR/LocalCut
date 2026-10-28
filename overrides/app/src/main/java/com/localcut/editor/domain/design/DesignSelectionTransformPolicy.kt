package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.ShapeKind

object DesignSelectionTransformPolicy {
    fun pathBoundsCenterPx(
        elements: List<DesignElement>,
        selectedIds: List<String>,
        canvasWidthPx: Double,
        canvasHeightPx: Double,
    ): CanvasPointPx {
        require(selectedIds.isNotEmpty()) { "design selection must not be empty" }
        require(selectedIds.all { it.isNotBlank() }) { "design selection ids must not be blank" }
        require(selectedIds.distinct().size == selectedIds.size) { "design selection ids must be unique" }
        require(canvasWidthPx.isFinite() && canvasWidthPx > 0.0) { "canvas width must be finite and positive" }
        require(canvasHeightPx.isFinite() && canvasHeightPx > 0.0) { "canvas height must be finite and positive" }

        val byId = elements.associateBy { it.id }
        val selected = selectedIds.map { id ->
            byId[id] ?: throw IllegalArgumentException("design element not found: $id")
        }
        require(selected.all { it.shape?.kind == ShapeKind.PATH && it.shape?.path != null }) {
            "design selection transform currently requires PATH elements"
        }

        val points = selected.flatMap { element ->
            DesignElementGeometryPolicy.transformedPathPointsPx(
                element = element,
                canvasWidthPx = canvasWidthPx,
                canvasHeightPx = canvasHeightPx,
            )
        }
        require(points.isNotEmpty()) { "design selection path geometry must not be empty" }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return CanvasPointPx(
            x = (minX + maxX) / 2.0,
            y = (minY + maxY) / 2.0,
        )
    }
}
