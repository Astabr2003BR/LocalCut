package com.localcut.editor.domain.history

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEditDrawingOperationTest {
    @Test
    fun deleteDrawingPreservesNonPathDesignAndUndoRestoresExactProject() {
        val original = fixtureProject()
        val result = ProjectEditBatchCommand(
            listOf(ProjectEditOperation.DeleteByKind(setOf(ProjectItemKind.DRAWING))),
        ).apply(original)

        assertEquals(listOf("rect-1"), result.project.designElements.map { it.id })
        assertEquals(original, result.inverse.apply(result.project).project)
    }

    @Test
    fun patchDrawingReplacesPathPointsAndRejectsPathPatchOnShape() {
        val original = fixtureProject()
        val replacement = listOf(PathPoint(0.2, 0.3), PathPoint(0.7, 0.8), PathPoint(0.9, 0.4))

        val result = ProjectEditBatchCommand(
            listOf(ProjectEditOperation.PatchDesign("stroke-1", points = replacement, closed = true)),
        ).apply(original)

        val path = result.project.designElements.first { it.id == "stroke-1" }.shape!!.path!!
        assertEquals(replacement, path.points)
        assertTrue(path.closed)
        assertEquals(original, result.inverse.apply(result.project).project)
        assertTrue(
            runCatching {
                ProjectEditBatchCommand(
                    listOf(ProjectEditOperation.PatchDesign("rect-1", points = replacement)),
                ).apply(original)
            }.isFailure,
        )
    }

    private fun fixtureProject() = Project(
        id = "p",
        schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
        title = "draw-test",
        width = 1080,
        height = 1920,
        fps = 30,
        assets = emptyList(),
        tracks = emptyList(),
        designElements = listOf(
            DesignElement(
                id = "stroke-1",
                startUs = 0L,
                durationUs = 1_000_000L,
                shape = ShapeSpec(
                    kind = ShapeKind.PATH,
                    path = VectorPath(listOf(PathPoint(0.1, 0.1), PathPoint(0.9, 0.9)), false),
                    fillArgb = null,
                    strokeArgb = 0xFFFFFFFF.toInt(),
                    strokeWidthPx = 4.0,
                ),
            ),
            DesignElement(
                id = "rect-1",
                startUs = 0L,
                durationUs = 1_000_000L,
                shape = ShapeSpec(ShapeKind.RECTANGLE),
            ),
        ),
    )
}
