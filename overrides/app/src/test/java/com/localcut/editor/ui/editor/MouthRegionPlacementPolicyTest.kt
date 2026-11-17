package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.MouthRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MouthRegionPlacementPolicyTest {
    private val original = MouthRegion(0.5, 0.68, 0.14, 0.08)

    @Test
    fun portraitImageFitsInsideWideCanvasWithSideBars() {
        val fit = MouthRegionPlacementPolicy.fit(900, 1600, 400.0, 240.0)
        assertEquals(132.5, fit.leftPx, 0.0001)
        assertEquals(0.0, fit.topPx, 0.0001)
        assertEquals(135.0, fit.widthPx, 0.0001)
        assertEquals(240.0, fit.heightPx, 0.0001)

        val center = MouthRegionPlacementPolicy.place(original, 200.0, 120.0, fit)
        assertEquals(0.5, center!!.centerX, 0.0001)
        assertEquals(0.5, center.centerY, 0.0001)
        assertNull(MouthRegionPlacementPolicy.place(original, 30.0, 120.0, fit))
    }

    @Test
    fun landscapeImageFitsTallCanvasWithTopAndBottomBars() {
        val fit = MouthRegionPlacementPolicy.fit(1600, 900, 250.0, 400.0)
        assertEquals(0.0, fit.leftPx, 0.0001)
        assertEquals(129.6875, fit.topPx, 0.0001)
        assertEquals(250.0, fit.widthPx, 0.0001)
        assertEquals(140.625, fit.heightPx, 0.0001)
        assertNull(MouthRegionPlacementPolicy.place(original, 125.0, 20.0, fit))
    }

    @Test
    fun imageTapMapsToNormalizedSourceAndClampsEllipseInsideImage() {
        val fit = MouthRegionPlacementPolicy.fit(400, 400, 200.0, 200.0)
        val changed = MouthRegionPlacementPolicy.place(original, 180.0, 40.0, fit)!!
        assertEquals(0.86, changed.centerX, 0.0001)
        assertEquals(0.20, changed.centerY, 0.0001)

        val onEdge = MouthRegionPlacementPolicy.place(original, 0.0, 200.0, fit)!!
        assertEquals(0.14, onEdge.centerX, 0.0001)
        assertEquals(0.92, onEdge.centerY, 0.0001)
    }

    @Test
    fun radiusAdjustmentKeepsCurrentCenterAndEllipseWithinImage() {
        val after = MouthRegionPlacementPolicy.withRadii(
            MouthRegion(0.91, 0.05, 0.10, 0.04), 0.30, 0.12,
        )
        assertEquals(0.70, after.centerX, 0.0001)
        assertEquals(0.12, after.centerY, 0.0001)
        assertEquals(0.30, after.radiusX, 0.0001)
        assertEquals(0.12, after.radiusY, 0.0001)
    }

    @Test
    fun ellipseBoundsUseExactlyTheSameFittedCoordinateSystem() {
        val fit = MouthRegionPlacementPolicy.fit(900, 1600, 400.0, 240.0)
        val rect = MouthRegionPlacementPolicy.ellipseBounds(original, fit)
        assertEquals(132.5 + 135.0 * 0.36, rect.leftPx, 0.0001)
        assertEquals(240.0 * 0.60, rect.topPx, 0.0001)
        assertEquals(135.0 * 0.28, rect.widthPx, 0.0001)
        assertEquals(240.0 * 0.16, rect.heightPx, 0.0001)
    }

    @Test
    fun invalidViewportsAndTouchCoordinatesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            MouthRegionPlacementPolicy.fit(0, 100, 200.0, 200.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MouthRegionPlacementPolicy.fit(100, 100, Double.NaN, 200.0)
        }
        val fit = MouthRegionPlacementPolicy.fit(100, 100, 200.0, 200.0)
        assertNull(MouthRegionPlacementPolicy.place(original, Double.NaN, 50.0, fit))
        assertNull(MouthRegionPlacementPolicy.place(original, -1.0, 50.0, fit))
        assertNull(MouthRegionPlacementPolicy.place(original, 100.0, Double.POSITIVE_INFINITY, fit))
    }

    @Test
    fun composeFloatEndpointsDoNotEscapeDoubleValidationBounds() {
        val tiny = MouthRegionPlacementPolicy.fromUiValues(0f, 1f, 0.01f, 0.005f)
        assertEquals(0.01, tiny.radiusX, 0.0)
        assertEquals(0.005, tiny.radiusY, 0.0)
        assertEquals(0.01, tiny.centerX, 0.0)
        assertEquals(0.995, tiny.centerY, 0.0)

        val maxed = MouthRegionPlacementPolicy.fromUiValues(0.9f, 0f, 0.50f, 0.30f)
        assertEquals(0.5, maxed.radiusX, 0.0)
        assertEquals(0.3, maxed.radiusY, 0.0)
        assertEquals(0.5, maxed.centerX, 0.0)
        assertEquals(0.3, maxed.centerY, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            MouthRegionPlacementPolicy.fromUiValues(Float.NaN, 0.5f, 0.1f, 0.1f)
        }
    }

    @Test
    fun tinyAndMaximumAllowedRadiiRemainValid() {
        val fit = MouthRegionPlacementPolicy.fit(200, 200, 200.0, 200.0)
        val maxRegion = MouthRegion(0.5, 0.5, 0.5, 0.3)
        val centered = MouthRegionPlacementPolicy.place(maxRegion, 190.0, 180.0, fit)!!
        assertTrue(centered.centerX == 0.5)
        assertEquals(0.7, centered.centerY, 0.0001)
    }
}
