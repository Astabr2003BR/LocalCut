package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeSymmetryPolicyTest {
    @Test
    fun noneReturnsOnlyOriginalStroke() {
        val points = listOf(CanvasPointPx(10.0, 20.0), CanvasPointPx(30.0, 40.0))

        assertEquals(
            listOf(points),
            StrokeSymmetryPolicy.expand(points, canvasWidthPx = 100.0, canvasHeightPx = 80.0, StrokeSymmetryMode.NONE),
        )
    }

    @Test
    fun verticalAxisProducesMirroredCompanionStroke() {
        val points = listOf(CanvasPointPx(10.0, 20.0), CanvasPointPx(30.0, 40.0))

        val result = StrokeSymmetryPolicy.expand(
            points,
            canvasWidthPx = 100.0,
            canvasHeightPx = 80.0,
            mode = StrokeSymmetryMode.VERTICAL_AXIS,
        )

        assertEquals(2, result.size)
        assertEquals(points, result[0])
        assertEquals(listOf(CanvasPointPx(90.0, 20.0), CanvasPointPx(70.0, 40.0)), result[1])
    }

    @Test
    fun horizontalAxisProducesMirroredCompanionStroke() {
        val points = listOf(CanvasPointPx(10.0, 20.0), CanvasPointPx(30.0, 40.0))

        val result = StrokeSymmetryPolicy.expand(
            points,
            canvasWidthPx = 100.0,
            canvasHeightPx = 80.0,
            mode = StrokeSymmetryMode.HORIZONTAL_AXIS,
        )

        assertEquals(listOf(CanvasPointPx(10.0, 60.0), CanvasPointPx(30.0, 40.0)), result[1])
    }

    @Test
    fun rejectsInvalidCanvasOrPoints() {
        val points = listOf(CanvasPointPx(10.0, 20.0), CanvasPointPx(30.0, 40.0))
        assertTrue(runCatching {
            StrokeSymmetryPolicy.expand(points, 0.0, 80.0, StrokeSymmetryMode.VERTICAL_AXIS)
        }.isFailure)
        assertTrue(runCatching {
            StrokeSymmetryPolicy.expand(
                listOf(CanvasPointPx(Double.NaN, 20.0), CanvasPointPx(30.0, 40.0)),
                100.0,
                80.0,
                StrokeSymmetryMode.VERTICAL_AXIS,
            )
        }.isFailure)
    }
}
