package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawStrokePolicyTest {
    @Test
    fun normalizesAndClampsCanvasPoints() {
        val path = DrawStrokePolicy.normalize(
            points = listOf(CanvasPointPx(-10.0, -20.0), CanvasPointPx(50.0, 25.0), CanvasPointPx(150.0, 70.0)),
            canvasWidthPx = 100.0,
            canvasHeightPx = 50.0,
        )

        assertEquals(0.0, path.first().x, 0.0)
        assertEquals(0.0, path.first().y, 0.0)
        assertEquals(1.0, path.last().x, 0.0)
        assertEquals(1.0, path.last().y, 0.0)
    }

    @Test
    fun boundsLargeStrokeToPocoSafePointBudget() {
        val points = (0..600).map { CanvasPointPx(it.toDouble(), it.toDouble()) }
        val path = DrawStrokePolicy.normalize(points, 600.0, 600.0)

        assertEquals(DrawStrokePolicy.MAX_POINTS, path.size)
        assertEquals(0.0, path.first().x, 0.0)
        assertEquals(1.0, path.last().x, 0.0)
    }

    @Test
    fun rejectsDegenerateStroke() {
        assertTrue(runCatching {
            DrawStrokePolicy.normalize(listOf(CanvasPointPx(1.0, 1.0)), 100.0, 100.0)
        }.isFailure)
        assertTrue(runCatching {
            DrawStrokePolicy.normalize(
                listOf(CanvasPointPx(1.0, 1.0), CanvasPointPx(1.0, 1.0)),
                100.0,
                100.0,
            )
        }.isFailure)
    }

    @Test
    fun createsRenderableOpenPathElement() {
        val element = DrawStrokePolicy.createPathElement(
            id = "stroke-1",
            startUs = 1_000_000L,
            durationUs = 2_000_000L,
            points = listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(100.0, 50.0)),
            canvasWidthPx = 100.0,
            canvasHeightPx = 50.0,
            strokeArgb = 0xFF102030.toInt(),
            strokeWidthPx = 12.0,
            zIndex = 7,
        )

        assertEquals(ShapeKind.PATH, element.shape?.kind)
        assertNull(element.shape?.fillArgb)
        assertEquals(0xFF102030.toInt(), element.shape?.strokeArgb)
        assertEquals(12.0, element.shape?.strokeWidthPx ?: -1.0, 0.0)
        assertEquals(false, element.shape?.path?.closed)
        assertEquals(7, element.zIndex)
        assertTrue(element.validationIssues().isEmpty())
    }
}
