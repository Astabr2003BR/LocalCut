package com.localcut.editor.ui.editor

import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.DesignGroup
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorLassoCapturePolicyTest {
    @Test
    fun captureUsesSuppliedCurrentProjectAndExpandsGroupMembership() {
        val currentA = path("current-a", x = 0.0)
        val currentB = path("current-b", x = 35.0)
        val currentProject = Project.create("Current", 100, 100, 30).copy(
            designElements = listOf(currentA, currentB),
            designGroups = listOf(DesignGroup("group-1", listOf(currentA.id, currentB.id))),
        )

        val captured = VectorLassoCapturePolicy.capture(
            project = currentProject,
            playheadUs = 500_000L,
            gesturePoints = centerSquare(),
            surfaceWidthPx = 100.0,
            surfaceHeightPx = 100.0,
        )

        assertEquals(listOf("current-a", "current-b"), captured)
    }

    @Test
    fun captureDoesNotReturnElementRemovedFromCurrentProjectSnapshot() {
        val replacement = path("replacement", x = 0.0)
        val currentProject = Project.create("Current", 100, 100, 30).copy(
            designElements = listOf(replacement),
        )

        val captured = VectorLassoCapturePolicy.capture(
            project = currentProject,
            playheadUs = 500_000L,
            gesturePoints = centerSquare(),
            surfaceWidthPx = 100.0,
            surfaceHeightPx = 100.0,
        )

        assertEquals(listOf("replacement"), captured)
    }

    @Test
    fun captureUsesSuppliedCurrentPlayhead() {
        val early = path("early", x = 0.0, startUs = 0L)
        val late = path("late", x = 0.0, startUs = 2_000_000L)
        val currentProject = Project.create("Current", 100, 100, 30).copy(
            designElements = listOf(early, late),
        )

        val captured = VectorLassoCapturePolicy.capture(
            project = currentProject,
            playheadUs = 2_500_000L,
            gesturePoints = centerSquare(),
            surfaceWidthPx = 100.0,
            surfaceHeightPx = 100.0,
        )

        assertEquals(listOf("late"), captured)
    }

    private fun centerSquare(): List<CanvasPointPx> = listOf(
        CanvasPointPx(35.0, 35.0),
        CanvasPointPx(65.0, 35.0),
        CanvasPointPx(65.0, 65.0),
        CanvasPointPx(35.0, 65.0),
    )

    private fun path(
        id: String,
        x: Double,
        startUs: Long = 0L,
    ): DesignElement = DesignElement(
        id = id,
        startUs = startUs,
        durationUs = 1_000_000L,
        shape = ShapeSpec(
            kind = ShapeKind.PATH,
            path = VectorPath(
                points = listOf(
                    PathPoint(0.25, 0.5),
                    PathPoint(0.75, 0.5),
                ),
            ),
        ),
        transform = TransformValue(x = x),
    )
}
