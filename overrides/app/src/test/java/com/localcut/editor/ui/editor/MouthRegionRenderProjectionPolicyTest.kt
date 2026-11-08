package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.MouthRegion
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MouthRegionRenderProjectionPolicyTest {
    private val region = MouthRegion(0.5, 0.68, 0.14, 0.08)

    @Test
    fun sameImageAndCanvasAspectPreservesSourceCoordinates() {
        val result = MouthRegionRenderProjectionPolicy.fromSourceToEffect(
            region, 1080, 1350, 2160, 2700,
        )
        assertEquals(region.centerX, result.centerX, 0.000001)
        assertEquals(region.centerY, result.centerY, 0.000001)
        assertEquals(region.radiusX, result.radiusX, 0.000001)
        assertEquals(region.radiusY, result.radiusY, 0.000001)
    }

    @Test
    fun fourByFiveImageOnNineBySixteenCanvasMapsToLetterboxedFrame() {
        val result = MouthRegionRenderProjectionPolicy.fromSourceToEffect(
            region, 1080, 1350, 1080, 1920,
        )
        assertEquals(0.5, result.centerX, 0.000001)
        assertEquals(0.6265625, result.centerY, 0.000001)
        assertEquals(0.14, result.radiusX, 0.000001)
        assertEquals(0.05625, result.radiusY, 0.000001)
    }

    @Test
    fun tallImageOnWideCanvasMapsXAndRadiusToActualPhotoArea() {
        val result = MouthRegionRenderProjectionPolicy.fromSourceToEffect(
            region.copy(centerX = 0.75), 900, 1600, 1600, 900,
        )
        val fitX = (900.0 / 1600.0) / (1600.0 / 900.0)
        assertEquals(0.5 + (0.75 - 0.5) * fitX, result.centerX, 0.000001)
        assertEquals(0.14 * fitX, result.radiusX, 0.000001)
        assertEquals(0.68, result.centerY, 0.000001)
    }

    @Test
    fun unsupportedAnimatedOrCroppedVisualsCannotClaimExactSourceSelection() {
        val project = Project.create("Mouth positioning", 1080, 1920, 30)
        val clip = Clip("image", "asset", 0L, 2_000_000L, 0L, 2_000_000L)
        assertTrue(MouthRegionRenderProjectionPolicy.supportsSourcePreview(project, clip))
        assertFalse(MouthRegionRenderProjectionPolicy.supportsSourcePreview(
            project, clip.copy(transform = TransformValue(x = 100.0)),
        ))
        assertFalse(MouthRegionRenderProjectionPolicy.supportsSourcePreview(
            project, clip.copy(compositing = CompositingSettings(crop = CropSettings(mode = CropMode.FILL))),
        ))
    }

    @Test
    fun tinyRenderedRegionFailsClosedInsteadOfMisplacingTheMouth() {
        assertThrows(IllegalArgumentException::class.java) {
            MouthRegionRenderProjectionPolicy.fromSourceToEffect(
                region.copy(radiusY = 0.005), 1920, 1080, 1080, 1920,
            )
        }
    }

    @Test
    fun invalidGeometryFailsBeforeRender() {
        assertThrows(IllegalArgumentException::class.java) {
            MouthRegionRenderProjectionPolicy.fromSourceToEffect(region, 0, 100, 1080, 1920)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MouthRegionRenderProjectionPolicy.fromSourceToEffect(
                region.copy(centerX = Double.NaN), 100, 100, 1080, 1920,
            )
        }
    }
}
