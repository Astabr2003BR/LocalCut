package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LineAngleSnapPolicyTest {
    @Test
    fun offKeepsLineUntouched() {
        val points = listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(10.0, 7.0))
        assertEquals(points, LineAngleSnapPolicy.apply(points, LineAngleSnapMode.OFF))
    }

    @Test
    fun fortyFiveDegreesSnapsNearDiagonal() {
        val points = listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(10.0, 8.0))
        val snapped = LineAngleSnapPolicy.apply(points, LineAngleSnapMode.DEG_45)
        val dx = snapped.last().x - snapped.first().x
        val dy = snapped.last().y - snapped.first().y

        assertTrue(abs(abs(dx) - abs(dy)) < 1e-9)
    }

    @Test
    fun fifteenDegreesPreservesLength() {
        val points = listOf(CanvasPointPx(3.0, 4.0), CanvasPointPx(15.0, 11.0))
        val originalLengthSquared = lengthSquared(points)
        val snapped = LineAngleSnapPolicy.apply(points, LineAngleSnapMode.DEG_15)

        assertEquals(originalLengthSquared, lengthSquared(snapped), 1e-8)
    }

    @Test
    fun rejectsInvalidLine() {
        assertTrue(runCatching {
            LineAngleSnapPolicy.apply(listOf(CanvasPointPx(0.0, 0.0)), LineAngleSnapMode.DEG_15)
        }.isFailure)
        assertTrue(runCatching {
            LineAngleSnapPolicy.apply(
                listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(Double.NaN, 1.0)),
                LineAngleSnapMode.DEG_15,
            )
        }.isFailure)
    }

    private fun lengthSquared(points: List<CanvasPointPx>): Double {
        val dx = points.last().x - points.first().x
        val dy = points.last().y - points.first().y
        return dx * dx + dy * dy
    }
}
