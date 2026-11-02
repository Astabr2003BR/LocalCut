package com.localcut.editor.media

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignOverlayRenderSpecCompilerTest {
    @Test
    fun compilesOnlyShapeElementsInDeterministicLayerOrder() {
        val project = Project.create("Design", 1000, 500, 30).copy(
            designElements = listOf(
                DesignElement(
                    id = "top-b",
                    startUs = 0L,
                    durationUs = 2_000_000L,
                    shape = ShapeSpec(ShapeKind.ELLIPSE),
                    zIndex = 5,
                ),
                DesignElement(
                    id = "top-a",
                    startUs = 0L,
                    durationUs = 2_000_000L,
                    shape = ShapeSpec(ShapeKind.RECTANGLE),
                    zIndex = 5,
                ),
                DesignElement(
                    id = "bottom",
                    startUs = 500_000L,
                    durationUs = 1_000_000L,
                    shape = ShapeSpec(ShapeKind.LINE, fillArgb = 0xFFFFFFFF.toInt()),
                    zIndex = -1,
                ),
                DesignElement(
                    id = "clip-reference",
                    startUs = 0L,
                    durationUs = 1_000_000L,
                    clipId = "missing-for-this-unit-only",
                    zIndex = 10,
                ),
            ),
        )

        val specs = DesignOverlayRenderSpecCompiler.compile(project, outputWidth = 2000, outputHeight = 1000)

        assertEquals(listOf("bottom", "top-a", "top-b"), specs.map { it.id })
        assertFalse(specs.first().isActiveAt(499_999L))
        assertTrue(specs.first().isActiveAt(500_000L))
        assertTrue(specs.first().isActiveAt(1_499_999L))
        assertFalse(specs.first().isActiveAt(1_500_000L))
    }

    @Test
    fun mapsProjectCoordinatesScaleAndOpacityToOutputPixels() {
        val element = DesignElement(
            id = "shape",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(ShapeKind.RECTANGLE, cornerRadiusPx = 12.0),
            transform = TransformValue(
                x = 100.0,
                y = 50.0,
                scale = 2.0,
                rotationDeg = 30.0,
                opacity = 0.5,
            ),
            opacity = 0.8,
        )
        val project = Project.create("Design", 1000, 500, 30).copy(designElements = listOf(element))

        val spec = DesignOverlayRenderSpecCompiler.compile(project, outputWidth = 2000, outputHeight = 1000).single()

        assertEquals(1200.0f, spec.centerX, 0.001f)
        assertEquals(600.0f, spec.centerY, 0.001f)
        assertEquals(500.0f, spec.widthPx, 0.001f)
        assertEquals(500.0f, spec.heightPx, 0.001f)
        assertEquals(30.0f, spec.rotationDegrees, 0.001f)
        assertEquals(0.4f, spec.alpha, 0.0001f)
        assertEquals(48.0f, spec.cornerRadiusPx, 0.001f)
    }
}
