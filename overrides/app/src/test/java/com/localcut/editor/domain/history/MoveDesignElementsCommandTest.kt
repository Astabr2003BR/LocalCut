package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveDesignElementsCommandTest {
    @Test
    fun movesSelectionAtomicallyAndUndoRedoRestoresExactTransforms() {
        val project = projectWith("a", "b", "c")

        val moved = MoveDesignElementsCommand(
            elementIds = listOf("b", "c"),
            dx = 40.0,
            dy = -20.0,
        ).apply(project)

        assertEquals(0.0, moved.project.designElements[0].transform.x, 0.0)
        assertEquals(40.0, moved.project.designElements[1].transform.x, 0.0)
        assertEquals(-20.0, moved.project.designElements[1].transform.y, 0.0)
        assertEquals(40.0, moved.project.designElements[2].transform.x, 0.0)
        assertEquals(-20.0, moved.project.designElements[2].transform.y, 0.0)

        val restored = moved.inverse.apply(moved.project)
        assertEquals(project.designElements, restored.project.designElements)

        val redone = restored.inverse.apply(restored.project)
        assertEquals(moved.project.designElements, redone.project.designElements)
    }

    @Test
    fun invalidSelectionOrDeltaFailsClosed() {
        val project = projectWith("a", "b")

        assertTrue(runCatching { MoveDesignElementsCommand(emptyList(), 1.0, 0.0).apply(project) }.isFailure)
        assertTrue(runCatching { MoveDesignElementsCommand(listOf("a", "a"), 1.0, 0.0).apply(project) }.isFailure)
        assertTrue(runCatching { MoveDesignElementsCommand(listOf("missing"), 1.0, 0.0).apply(project) }.isFailure)
        assertTrue(runCatching { MoveDesignElementsCommand(listOf("a"), Double.NaN, 0.0).apply(project) }.isFailure)
        assertTrue(runCatching { MoveDesignElementsCommand(listOf("a"), 0.0, 0.0).apply(project) }.isFailure)
        assertEquals(project.designElements, project.designElements)
    }

    private fun projectWith(vararg ids: String): Project {
        val base = Project.create("design", 1080, 1920, 30)
        return base.copy(
            designElements = ids.mapIndexed { index, id ->
                DesignElement(
                    id = id,
                    startUs = 0L,
                    durationUs = 1_000_000L,
                    shape = ShapeSpec(kind = ShapeKind.RECTANGLE),
                    zIndex = index,
                )
            },
        )
    }
}
