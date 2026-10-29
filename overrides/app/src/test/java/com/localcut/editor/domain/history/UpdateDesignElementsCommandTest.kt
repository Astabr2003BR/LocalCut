package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDesignElementsCommandTest {
    private fun element(id: String, x: Double) = DesignElement(
        id = id,
        startUs = 0L,
        durationUs = 1_000_000L,
        shape = ShapeSpec(
            kind = ShapeKind.PATH,
            path = VectorPath(listOf(PathPoint(0.0, 0.0), PathPoint(1.0, 1.0))),
        ),
        transform = TransformValue(x = x),
    )

    @Test
    fun batchUpdateIsOneUndoRedoOperationAndPreservesUnselectedElements() {
        val a = element("a", -100.0)
        val b = element("b", 0.0)
        val c = element("c", 100.0)
        val initial = Project.create("Layout", 1080, 1920, 30).copy(designElements = listOf(a, b, c))
        val history = HistoryManager(initial)

        history.execute(
            UpdateDesignElementsCommand(
                listOf(
                    a.copy(transform = a.transform.copy(x = -50.0)),
                    c.copy(transform = c.transform.copy(x = 50.0)),
                ),
            ),
        )

        assertEquals(listOf(-50.0, 0.0, 50.0), history.project.designElements.map { it.transform.x })
        assertTrue(history.undo())
        assertEquals(initial, history.project)
        assertTrue(history.redo())
        assertEquals(listOf(-50.0, 0.0, 50.0), history.project.designElements.map { it.transform.x })
    }

    @Test
    fun batchUpdateRejectsMissingOrDuplicateIds() {
        val a = element("a", 0.0)
        val initial = Project.create("Layout", 1080, 1920, 30).copy(designElements = listOf(a))

        assertTrue(runCatching {
            UpdateDesignElementsCommand(listOf(element("missing", 1.0))).apply(initial)
        }.isFailure)
        assertTrue(runCatching {
            UpdateDesignElementsCommand(listOf(a, a.copy(transform = a.transform.copy(x = 2.0)))).apply(initial)
        }.isFailure)
    }
}
