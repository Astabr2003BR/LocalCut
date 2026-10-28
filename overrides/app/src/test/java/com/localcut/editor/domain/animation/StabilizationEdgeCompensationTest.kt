package com.localcut.editor.domain.animation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilizationEdgeCompensationTest {
    @Test
    fun neutralCorrectionRequiresNoOverscan() {
        assertEquals(
            1.0,
            StabilizationEdgeCompensation.requiredCoverageScale(
                canvasWidth = 1080,
                canvasHeight = 1920,
                translationX = 0.0,
                translationY = 0.0,
                rotationDeg = 0.0,
            ),
            1e-9,
        )
    }

    @Test
    fun tenPercentTranslationRequiresTenPercentOverscanOnThatAxis() {
        assertEquals(
            1.1,
            StabilizationEdgeCompensation.requiredCoverageScale(
                canvasWidth = 1080,
                canvasHeight = 1920,
                translationX = 54.0,
                translationY = 0.0,
                rotationDeg = 0.0,
            ),
            1e-9,
        )
        assertEquals(
            1.1,
            StabilizationEdgeCompensation.requiredCoverageScale(
                canvasWidth = 1080,
                canvasHeight = 1920,
                translationX = 0.0,
                translationY = 96.0,
                rotationDeg = 0.0,
            ),
            1e-9,
        )
    }

    @Test
    fun returnedScaleContainsAllCanvasCornersAfterInverseCorrection() {
        val width = 1080
        val height = 1920
        val tx = -48.0
        val ty = 31.0
        val rotation = 7.0
        val scale = StabilizationEdgeCompensation.requiredCoverageScale(
            canvasWidth = width,
            canvasHeight = height,
            translationX = tx,
            translationY = ty,
            rotationDeg = rotation,
        )

        val radians = rotation * PI / 180.0
        val c = cos(radians)
        val s = sin(radians)
        val halfW = width / 2.0
        val halfH = height / 2.0
        listOf(-halfW, halfW).forEach { x ->
            listOf(-halfH, halfH).forEach { y ->
                val shiftedX = x - tx
                val shiftedY = y - ty
                val localX = c * shiftedX + s * shiftedY
                val localY = -s * shiftedX + c * shiftedY
                assertTrue(abs(localX) <= halfW * scale + 1e-7)
                assertTrue(abs(localY) <= halfH * scale + 1e-7)
            }
        }
    }

    @Test
    fun quarterTurnOnPortraitCanvasUsesAspectRatioCoverage() {
        assertEquals(
            1920.0 / 1080.0,
            StabilizationEdgeCompensation.requiredCoverageScale(
                canvasWidth = 1080,
                canvasHeight = 1920,
                translationX = 0.0,
                translationY = 0.0,
                rotationDeg = 90.0,
            ),
            1e-9,
        )
    }

    @Test
    fun invalidGeometryFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            StabilizationEdgeCompensation.requiredCoverageScale(0, 1920, 0.0, 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StabilizationEdgeCompensation.requiredCoverageScale(1080, 1920, Double.NaN, 0.0, 0.0)
        }
    }
}
