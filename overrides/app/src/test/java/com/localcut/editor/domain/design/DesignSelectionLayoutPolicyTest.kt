package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSelectionLayoutPolicyTest {
    private val square = ShapeSpec(
        kind = ShapeKind.PATH,
        path = VectorPath(
            points = listOf(
                PathPoint(0.0, 0.0),
                PathPoint(1.0, 0.0),
                PathPoint(1.0, 1.0),
                PathPoint(0.0, 1.0),
                PathPoint(0.0, 0.0),
            ),
            closed = true,
        ),
    )

    private fun element(id: String, x: Double, y: Double) = DesignElement(
        id = id,
        startUs = 0L,
        durationUs = 1_000_000L,
        shape = square,
        transform = TransformValue(x = x, y = y),
    )

    @Test
    fun alignsRealPathBoundsWithoutDestroyingOtherTransformFields() {
        val elements = listOf(
            element("a", -300.0, -100.0),
            element("b", -100.0, 50.0).copy(transform = TransformValue(x = -100.0, y = 50.0, scale = 1.2, rotationDeg = 30.0)),
            element("c", 300.0, 200.0),
        )

        val aligned = DesignSelectionLayoutPolicy.alignLeft(elements, 1000.0, 1000.0)
        val lefts = aligned.map { DesignSelectionLayoutPolicy.boundsFor(it, 1000.0, 1000.0).minX }

        assertTrue(lefts.all { kotlin.math.abs(it - lefts.first()) < 1e-9 })
        assertEquals(1.2, aligned[1].transform.scale, 0.0)
        assertEquals(30.0, aligned[1].transform.rotationDeg, 0.0)
    }

    @Test
    fun distributesHorizontalAndVerticalGapsWhileKeepingOuterBoundsFixed() {
        val horizontalSource = listOf(
            element("a", -300.0, 0.0),
            element("b", -100.0, 0.0),
            element("c", 300.0, 0.0),
        )
        val horizontal = DesignSelectionLayoutPolicy.distributeHorizontally(horizontalSource, 1000.0, 1000.0)
        assertEquals(-300.0, horizontal[0].transform.x, 1e-9)
        assertEquals(0.0, horizontal[1].transform.x, 1e-9)
        assertEquals(300.0, horizontal[2].transform.x, 1e-9)

        val verticalSource = listOf(
            element("a", 0.0, -300.0),
            element("b", 0.0, -100.0),
            element("c", 0.0, 300.0),
        )
        val vertical = DesignSelectionLayoutPolicy.distributeVertically(verticalSource, 1000.0, 1000.0)
        assertEquals(-300.0, vertical[0].transform.y, 1e-9)
        assertEquals(0.0, vertical[1].transform.y, 1e-9)
        assertEquals(300.0, vertical[2].transform.y, 1e-9)
    }

    @Test
    fun rejectsNonPathAndInsufficientDistributionSelection() {
        val nonPath = DesignElement(
            id = "rect",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(ShapeKind.RECTANGLE),
        )
        assertTrue(runCatching {
            DesignSelectionLayoutPolicy.alignLeft(listOf(element("a", 0.0, 0.0), nonPath), 1000.0, 1000.0)
        }.isFailure)
        assertTrue(runCatching {
            DesignSelectionLayoutPolicy.distributeHorizontally(
                listOf(element("a", 0.0, 0.0), element("b", 10.0, 0.0)),
                1000.0,
                1000.0,
            )
        }.isFailure)
    }
}
