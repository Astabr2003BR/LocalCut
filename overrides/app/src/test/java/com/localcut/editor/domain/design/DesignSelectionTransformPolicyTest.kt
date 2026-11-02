package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DesignSelectionTransformPolicyTest {
    @Test
    fun pathBoundsCenterUsesRendererCompatibleTransformedGeometry() {
        val left = path("left", x = -100.0)
        val right = path("right", x = 100.0)

        val pivot = DesignSelectionTransformPolicy.pathBoundsCenterPx(
            elements = listOf(left, right),
            selectedIds = listOf("left", "right"),
            canvasWidthPx = 1000.0,
            canvasHeightPx = 800.0,
        )

        assertEquals(500.0, pivot.x, 0.0)
        assertEquals(400.0, pivot.y, 0.0)
    }

    @Test
    fun missingOrNonPathSelectionFailsClosed() {
        val path = path("path", x = 0.0)
        val rectangle = DesignElement(
            id = "rect",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(kind = ShapeKind.RECTANGLE),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DesignSelectionTransformPolicy.pathBoundsCenterPx(
                elements = listOf(path),
                selectedIds = listOf("missing"),
                canvasWidthPx = 1000.0,
                canvasHeightPx = 800.0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DesignSelectionTransformPolicy.pathBoundsCenterPx(
                elements = listOf(path, rectangle),
                selectedIds = listOf("path", "rect"),
                canvasWidthPx = 1000.0,
                canvasHeightPx = 800.0,
            )
        }
    }

    private fun path(id: String, x: Double): DesignElement {
        val base = DesignElement(
            id = id,
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(
                    points = listOf(
                        PathPoint(0.0, 0.0),
                        PathPoint(1.0, 1.0),
                    ),
                ),
            ),
        )
        return base.copy(transform = base.transform.copy(x = x))
    }
}
