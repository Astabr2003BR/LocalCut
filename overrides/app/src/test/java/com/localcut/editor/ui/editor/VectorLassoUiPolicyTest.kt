package com.localcut.editor.ui.editor

import com.localcut.editor.domain.design.CanvasPointPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorLassoUiPolicyTest {
    @Test
    fun mapsSurfaceGestureIntoProjectCoordinatesAndClampsEdges() {
        val mapped = VectorLassoUiPolicy.toProjectPolygon(
            points = listOf(
                CanvasPointPx(-10.0, 0.0),
                CanvasPointPx(100.0, 50.0),
                CanvasPointPx(210.0, 100.0),
            ),
            surfaceWidthPx = 200.0,
            surfaceHeightPx = 100.0,
            projectWidthPx = 1080.0,
            projectHeightPx = 1920.0,
        )

        assertEquals(CanvasPointPx(0.0, 0.0), mapped[0])
        assertEquals(CanvasPointPx(540.0, 960.0), mapped[1])
        assertEquals(CanvasPointPx(1080.0, 1920.0), mapped[2])
    }

    @Test
    fun denseGestureIsBoundedButKeepsFirstAndLastPoint() {
        val source = (0..400).map { index -> CanvasPointPx(index.toDouble(), (index % 17).toDouble()) }
        val mapped = VectorLassoUiPolicy.toProjectPolygon(
            points = source,
            surfaceWidthPx = 400.0,
            surfaceHeightPx = 20.0,
            projectWidthPx = 1000.0,
            projectHeightPx = 500.0,
        )

        assertTrue(mapped.size <= VectorLassoUiPolicy.MAX_POINTS)
        assertEquals(CanvasPointPx(0.0, 0.0), mapped.first())
        assertEquals(CanvasPointPx(1000.0, 225.0), mapped.last())
    }

    @Test
    fun invalidSurfaceOrPolygonFailsClosed() {
        val triangle = listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(10.0, 0.0), CanvasPointPx(0.0, 10.0))
        assertThrows(IllegalArgumentException::class.java) {
            VectorLassoUiPolicy.toProjectPolygon(triangle, 0.0, 100.0, 1080.0, 1920.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VectorLassoUiPolicy.toProjectPolygon(triangle.take(2), 100.0, 100.0, 1080.0, 1920.0)
        }
    }
}
