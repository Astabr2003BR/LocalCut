package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeAssistPolicyTest {
    @Test
    fun freehandKeepsCapturedPoints() {
        val points = listOf(
            CanvasPointPx(0.0, 0.0),
            CanvasPointPx(5.0, 8.0),
            CanvasPointPx(10.0, 2.0),
        )

        assertEquals(points, StrokeAssistPolicy.apply(points, StrokeAssistMode.FREEHAND))
    }

    @Test
    fun lineModeUsesOnlyFirstAndLastPoint() {
        val points = listOf(
            CanvasPointPx(0.0, 0.0),
            CanvasPointPx(5.0, 8.0),
            CanvasPointPx(10.0, 2.0),
        )

        assertEquals(
            listOf(points.first(), points.last()),
            StrokeAssistPolicy.apply(points, StrokeAssistMode.LINE),
        )
    }

    @Test
    fun lineModeRejectsInvalidInput() {
        assertTrue(runCatching {
            StrokeAssistPolicy.apply(listOf(CanvasPointPx(0.0, 0.0)), StrokeAssistMode.LINE)
        }.isFailure)
        assertTrue(runCatching {
            StrokeAssistPolicy.apply(
                listOf(CanvasPointPx(Double.NaN, 0.0), CanvasPointPx(1.0, 1.0)),
                StrokeAssistMode.LINE,
            )
        }.isFailure)
    }
}
