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
    fun tenPercentPreScaleTranslationRequiresReciprocalCoverage() {
        val expected = 1.0 / 0.9
        assertEquals(
            expected,
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
            expected,
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
    fun returnedScaleContainsAllNdcCornersForActualPostTransformOrder() {
        val width = 1080
        val height = 1920
        val txPx = -48.0
        val tyPx = 31.0
        val rotation = 7.0
        val scale = StabilizationEdgeCompensation.requiredCoverageScale(
            canvasWidth = width,
            canvasHeight = height,
            translationX = txPx,
            translationY = tyPx,
            rotationDeg = rotation,
        )

        val tx = txPx / (width / 2.0)
        val ty = tyPx / (height / 2.0)
        val radians = rotation * PI / 180.0
        val c = cos(radians)
        val s = sin(radians)
        listOf(-1.0, 1.0).forEach { x ->
            listOf(-1.0, 1.0).forEach { y ->
                // Renderer order: M = S * R * T. Invert target NDC corner back to source NDC.
                val unscaledX = x / scale
                val unscaledY = y / scale
                val unrotatedX = c * unscaledX + s * unscaledY
                val unrotatedY = -s * unscaledX + c * unscaledY
                val sourceX = unrotatedX - tx
                val sourceY = unrotatedY - ty
                assertTrue(abs(sourceX) <= 1.0 + 1e-7)
                assertTrue(abs(sourceY) <= 1.0 + 1e-7)
            }
        }
    }

    @Test
    fun quarterTurnNeedsNoExtraCoverageInMedia3NdcSquare() {
        assertEquals(
            1.0,
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
    fun translationAtOrBeyondNdcBoundaryCannotBeRecoveredByScaling() {
        assertThrows(IllegalArgumentException::class.java) {
            StabilizationEdgeCompensation.requiredCoverageScale(
                canvasWidth = 1080,
                canvasHeight = 1920,
                translationX = 540.0,
                translationY = 0.0,
                rotationDeg = 0.0,
            )
        }
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
