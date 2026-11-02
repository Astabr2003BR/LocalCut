package com.localcut.editor.ui.editor

import com.localcut.editor.domain.design.CanvasPointPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawStrokePadPolicyTest {
    @Test
    fun allocatesStableStrokeIds() {
        assertEquals("stroke-1", DrawStrokePadPolicy.nextStrokeId(emptySet()))
        assertEquals(
            "stroke-3",
            DrawStrokePadPolicy.nextStrokeId(setOf("stroke-1", "stroke-2", "shape-1")),
        )
    }

    @Test
    fun allocatesNextZIndex() {
        assertEquals(0, DrawStrokePadPolicy.nextZIndex(emptyList()))
        assertEquals(5, DrawStrokePadPolicy.nextZIndex(listOf(-2, 4, 1)))
    }

    @Test
    fun defaultsStayInsideDrawCoreLimits() {
        assertEquals(3_000_000L, DrawStrokePadPolicy.DEFAULT_DURATION_US)
        assertTrue(DrawStrokePadPolicy.DEFAULT_STROKE_WIDTH_PX in 0.5..256.0)
        assertTrue(DrawStrokePadPolicy.DEFAULT_STABILIZATION_STRENGTH in 0..100)
    }

    @Test
    fun preparesStrokeUsingSelectedStabilizationStrength() {
        val points = listOf(
            CanvasPointPx(0.0, 0.0),
            CanvasPointPx(1.0, 8.0),
            CanvasPointPx(2.0, -8.0),
            CanvasPointPx(3.0, 8.0),
            CanvasPointPx(4.0, 0.0),
        )

        val unchanged = DrawStrokePadPolicy.prepareStrokePoints(points, stabilizationStrength = 0)
        val stabilized = DrawStrokePadPolicy.prepareStrokePoints(points, stabilizationStrength = 100)

        assertEquals(points, unchanged)
        assertEquals(points.first(), stabilized.first())
        assertEquals(points.last(), stabilized.last())
        assertTrue(kotlin.math.abs(stabilized[2].y) < kotlin.math.abs(points[2].y))
    }
}
