package com.localcut.editor.ui.editor

import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.design.DesignGroupSelectionPolicy
import com.localcut.editor.domain.design.VectorLassoSelectionPolicy
import com.localcut.editor.domain.model.Project

object VectorLassoCapturePolicy {
    fun capture(
        project: Project,
        playheadUs: Long,
        gesturePoints: List<CanvasPointPx>,
        surfaceWidthPx: Double,
        surfaceHeightPx: Double,
    ): List<String> {
        val polygon = VectorLassoUiPolicy.toProjectPolygon(
            points = gesturePoints,
            surfaceWidthPx = surfaceWidthPx,
            surfaceHeightPx = surfaceHeightPx,
            projectWidthPx = project.width.toDouble(),
            projectHeightPx = project.height.toDouble(),
        )
        val selected = VectorLassoSelectionPolicy.selectPathIds(
            elements = project.designElements,
            lassoPointsPx = polygon,
            canvasWidthPx = project.width.toDouble(),
            canvasHeightPx = project.height.toDouble(),
            playheadUs = playheadUs,
        )
        val captured = VectorLassoPersistentSelectionPolicy.capture(selected)
        return DesignGroupSelectionPolicy.expand(
            selectedIds = captured,
            groups = project.designGroups,
            existingElementIds = project.designElements.map { it.id },
        )
    }
}
