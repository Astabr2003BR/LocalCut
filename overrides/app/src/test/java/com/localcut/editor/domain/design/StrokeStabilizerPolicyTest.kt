package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeStabilizerPolicyTest {
    @Test
    fun zeroStrengthPreservesInputExactly() {
        val points = listOf(
            CanvasPointPx(0.0, 0.0),
            CanvasPointPx(10.0, 8.0),
            CanvasPointPx(20.0, -8.0),
            CanvasPointPx(30.0, 0.0),
        )

        assertEquals(points, StrokeStabilizerPolicy.smooth(points, strength = 0))
    }

    @Test
    fun fullStrengthReducesJitterAndPreservesEndpoints() {
        val points = listOf(
            CanvasPointPx(0.0, 0.0),
            CanvasPointPx(10.0, 20.0),
            CanvasPointPx(20.0, -20.0),
            CanvasPointPx(30.0, 20.0),
            CanvasPointPx(40.0, 0.0),
        )

        val smoothed = StrokeStabilizerPolicy.smooth(points, strength = 100)

        assertEquals(points.first(), smoothed.first())
        assertEquals(points.last(), smoothed.last())
        assertEquals(points.size, smoothed.size)
        val originalJitter = points.drop(1).dropLast(1).sumOf { kotlin.math.abs(it.y) }
        val smoothedJitter = smoothed.drop(1).dropLast(1).sumOf { kotlin.math.abs(it.y) }
        assertTrue(smoothedJitter < originalJitter)
    }

    @Test
    fun rejectsStrengthOutsideSupportedRange() {
        val points = listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(1.0, 1.0))

        assertTrue(runCatching { StrokeStabilizerPolicy.smooth(points, strength = -1) }.isFailure)
        assertTrue(runCatching { StrokeStabilizerPolicy.smooth(points, strength = 101) }.isFailure)
    }

    @Test
    fun rejectsNonFinitePoints() {
        val points = listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(Double.NaN, 1.0))

        assertTrue(runCatching { StrokeStabilizerPolicy.smooth(points, strength = 50) }.isFailure)
    }
}
