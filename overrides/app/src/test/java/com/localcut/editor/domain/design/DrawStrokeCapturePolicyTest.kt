package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawStrokeCapturePolicyTest {
    @Test
    fun ignoresTinyPointerJitter() {
        var points = emptyList<CanvasPointPx>()
        points = DrawStrokeCapturePolicy.append(points, CanvasPointPx(0.0, 0.0))
        points = DrawStrokeCapturePolicy.append(points, CanvasPointPx(0.1, 0.1))
        assertEquals(1, points.size)

        points = DrawStrokeCapturePolicy.append(points, CanvasPointPx(10.0, 10.0))
        assertEquals(2, points.size)
    }

    @Test
    fun boundsLongGestureAndKeepsLatestPoint() {
        var points = emptyList<CanvasPointPx>()
        repeat(2000) { index ->
            points = DrawStrokeCapturePolicy.append(points, CanvasPointPx(index.toDouble(), index.toDouble()))
        }

        assertTrue(points.size <= DrawStrokeCapturePolicy.MAX_RAW_POINTS)
        assertEquals(CanvasPointPx(1999.0, 1999.0), points.last())
    }

    @Test
    fun requiresRealStrokeBeforeCommit() {
        assertFalse(DrawStrokeCapturePolicy.canCommit(listOf(CanvasPointPx(0.0, 0.0))))
        assertTrue(
            DrawStrokeCapturePolicy.canCommit(
                listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(20.0, 20.0)),
            ),
        )
    }
}
