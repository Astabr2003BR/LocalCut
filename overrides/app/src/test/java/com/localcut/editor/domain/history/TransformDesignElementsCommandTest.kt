package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransformDesignElementsCommandTest {
    @Test
    fun scaleAndRotateSelectionAroundSharedPivotAtomicallyWithUndoRedo() {
        val project = projectWith(
            element("left", x = -100.0),
            element("right", x = 100.0),
            element("untouched", x = 300.0),
        )

        val transformed = TransformDesignElementsCommand(
            elementIds = listOf("left", "right"),
            pivotX = 500.0,
            pivotY = 400.0,
            scaleFactor = 2.0,
            rotationDeltaDeg = 90.0,
        ).apply(project)

        val left = transformed.project.designElements.first { it.id == "left" }
        val right = transformed.project.designElements.first { it.id == "right" }
        val untouched = transformed.project.designElements.first { it.id == "untouched" }

        assertEquals(0.0, left.transform.x, 1e-9)
        assertEquals(-200.0, left.transform.y, 1e-9)
        assertEquals(2.0, left.transform.scale, 1e-9)
        assertEquals(90.0, left.transform.rotationDeg, 1e-9)

        assertEquals(0.0, right.transform.x, 1e-9)
        assertEquals(200.0, right.transform.y, 1e-9)
        assertEquals(2.0, right.transform.scale, 1e-9)
        assertEquals(90.0, right.transform.rotationDeg, 1e-9)

        assertEquals(300.0, untouched.transform.x, 0.0)
        assertEquals(1.0, untouched.transform.scale, 0.0)

        val restored = transformed.inverse.apply(transformed.project)
        assertEquals(project.designElements.map { it.transform }, restored.project.designElements.map { it.transform })

        val redone = restored.inverse.apply(restored.project)
        assertEquals(transformed.project.designElements.map { it.transform }, redone.project.designElements.map { it.transform })
    }

    @Test
    fun invalidIdsPivotFactorOrNoopFailBeforeMutation() {
        val project = projectWith(element("a", x = 0.0))

        assertThrows(IllegalArgumentException::class.java) {
            TransformDesignElementsCommand(emptyList(), 500.0, 400.0, 1.1, 0.0).apply(project)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransformDesignElementsCommand(listOf("a", "a"), 500.0, 400.0, 1.1, 0.0).apply(project)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransformDesignElementsCommand(listOf("missing"), 500.0, 400.0, 1.1, 0.0).apply(project)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransformDesignElementsCommand(listOf("a"), Double.NaN, 400.0, 1.1, 0.0).apply(project)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransformDesignElementsCommand(listOf("a"), 500.0, 400.0, 0.0, 0.0).apply(project)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransformDesignElementsCommand(listOf("a"), 500.0, 400.0, 1.0, 0.0).apply(project)
        }
    }

    private fun projectWith(vararg elements: DesignElement): Project =
        Project.create("transform", 1000, 800, 30).copy(designElements = elements.toList())

    private fun element(id: String, x: Double): DesignElement {
        val base = DesignElement(
            id = id,
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(points = listOf(PathPoint(0.0, 0.0), PathPoint(1.0, 1.0))),
            ),
        )
        return base.copy(transform = base.transform.copy(x = x))
    }
}
