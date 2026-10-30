package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObjectTrackingRoiMappingPolicyTest {
    @Test
    fun identityMapsPreviewSelectionDirectlyToSource() {
        val roi = ObjectTrackingRoiMappingPolicy.map(
            selection = PreviewSelectionRect(250.0, 250.0, 750.0, 750.0),
            viewportWidthPx = 1000.0,
            viewportHeightPx = 1000.0,
            sourceWidthPx = 1000,
            sourceHeightPx = 1000,
            outputWidthPx = 1000,
            outputHeightPx = 1000,
            transformSpaceWidthPx = 1000.0,
            transformSpaceHeightPx = 1000.0,
            effectiveTransform = TransformValue(),
            crop = CropSettings(),
        )

        assertRoi(roi, 0.25, 0.25, 0.5, 0.5)
    }

    @Test
    fun playerViewLetterboxIsRemovedBeforeMapping() {
        val roi = ObjectTrackingRoiMappingPolicy.map(
            selection = PreviewSelectionRect(0.0, 218.75, 1000.0, 781.25),
            viewportWidthPx = 1000.0,
            viewportHeightPx = 1000.0,
            sourceWidthPx = 1600,
            sourceHeightPx = 900,
            outputWidthPx = 1600,
            outputHeightPx = 900,
            transformSpaceWidthPx = 1600.0,
            transformSpaceHeightPx = 900.0,
            effectiveTransform = TransformValue(),
            crop = CropSettings(),
        )

        assertRoi(roi, 0.0, 0.0, 1.0, 1.0)
    }

    @Test
    fun initialMediaFitPillarboxIsRejectedOutsideVisibleSourceAndMapsVisibleBounds() {
        val fullSource = ObjectTrackingRoiMappingPolicy.map(
            selection = PreviewSelectionRect(200.0, 0.0, 1400.0, 900.0),
            viewportWidthPx = 1600.0,
            viewportHeightPx = 900.0,
            sourceWidthPx = 1200,
            sourceHeightPx = 900,
            outputWidthPx = 1600,
            outputHeightPx = 900,
            transformSpaceWidthPx = 1600.0,
            transformSpaceHeightPx = 900.0,
            effectiveTransform = TransformValue(),
            crop = CropSettings(),
        )
        assertRoi(fullSource, 0.0, 0.0, 1.0, 1.0)

        val error = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingRoiMappingPolicy.map(
                selection = PreviewSelectionRect(0.0, 0.0, 1600.0, 900.0),
                viewportWidthPx = 1600.0,
                viewportHeightPx = 900.0,
                sourceWidthPx = 1200,
                sourceHeightPx = 900,
                outputWidthPx = 1600,
                outputHeightPx = 900,
                transformSpaceWidthPx = 1600.0,
                transformSpaceHeightPx = 900.0,
                effectiveTransform = TransformValue(),
                crop = CropSettings(),
            )
        }
        assertEquals("OBJECT_TRACKING_ROI_OUTSIDE_RENDERED_MEDIA", error.message)
    }

    @Test
    fun explicitCropMapsBackToOriginalSourceCoordinates() {
        val roi = ObjectTrackingRoiMappingPolicy.map(
            selection = PreviewSelectionRect(0.0, 0.0, 500.0, 500.0),
            viewportWidthPx = 500.0,
            viewportHeightPx = 500.0,
            sourceWidthPx = 1000,
            sourceHeightPx = 1000,
            outputWidthPx = 1000,
            outputHeightPx = 1000,
            transformSpaceWidthPx = 1000.0,
            transformSpaceHeightPx = 1000.0,
            effectiveTransform = TransformValue(),
            crop = CropSettings(
                mode = CropMode.NONE,
                left = 0.25,
                top = 0.25,
                right = 0.75,
                bottom = 0.75,
            ),
        )

        assertRoi(roi, 0.25, 0.25, 0.5, 0.5)
    }

    @Test
    fun inverseClipTranslationMatchesRenderedPosition() {
        val roi = ObjectTrackingRoiMappingPolicy.map(
            selection = PreviewSelectionRect(550.0, 450.0, 650.0, 550.0),
            viewportWidthPx = 1000.0,
            viewportHeightPx = 1000.0,
            sourceWidthPx = 1000,
            sourceHeightPx = 1000,
            outputWidthPx = 1000,
            outputHeightPx = 1000,
            transformSpaceWidthPx = 1000.0,
            transformSpaceHeightPx = 1000.0,
            effectiveTransform = TransformValue(x = 100.0),
            crop = CropSettings(),
        )

        assertRoi(roi, 0.45, 0.45, 0.1, 0.1)
    }

    @Test
    fun fitModeRejectsSelectionInsideInternalLetterbox() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingRoiMappingPolicy.map(
                selection = PreviewSelectionRect(0.0, 0.0, 100.0, 100.0),
                viewportWidthPx = 1600.0,
                viewportHeightPx = 900.0,
                sourceWidthPx = 1600,
                sourceHeightPx = 900,
                outputWidthPx = 1600,
                outputHeightPx = 900,
                transformSpaceWidthPx = 1600.0,
                transformSpaceHeightPx = 900.0,
                effectiveTransform = TransformValue(),
                crop = CropSettings(
                    mode = CropMode.FIT,
                    left = 0.25,
                    top = 0.0,
                    right = 0.75,
                    bottom = 1.0,
                ),
            )
        }
        assertEquals("OBJECT_TRACKING_ROI_OUTSIDE_RENDERED_MEDIA", error.message)
    }

    @Test
    fun rejectsSelectionOutsidePlayerContentAndTooSmallSourceRoi() {
        val outside = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingRoiMappingPolicy.map(
                selection = PreviewSelectionRect(0.0, 0.0, 100.0, 100.0),
                viewportWidthPx = 1000.0,
                viewportHeightPx = 1000.0,
                sourceWidthPx = 1600,
                sourceHeightPx = 900,
                outputWidthPx = 1600,
                outputHeightPx = 900,
                transformSpaceWidthPx = 1600.0,
                transformSpaceHeightPx = 900.0,
                effectiveTransform = TransformValue(),
                crop = CropSettings(),
            )
        }
        assertEquals("OBJECT_TRACKING_ROI_OUTSIDE_PREVIEW_CONTENT", outside.message)

        val tiny = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingRoiMappingPolicy.map(
                selection = PreviewSelectionRect(500.0, 500.0, 505.0, 505.0),
                viewportWidthPx = 1000.0,
                viewportHeightPx = 1000.0,
                sourceWidthPx = 1000,
                sourceHeightPx = 1000,
                outputWidthPx = 1000,
                outputHeightPx = 1000,
                transformSpaceWidthPx = 1000.0,
                transformSpaceHeightPx = 1000.0,
                effectiveTransform = TransformValue(),
                crop = CropSettings(),
            )
        }
        assertEquals("OBJECT_TRACKING_ROI_TOO_SMALL", tiny.message)
    }

    private fun assertRoi(
        actual: NormalizedMediaRoi,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ) {
        assertEquals(x, actual.x, 1e-6)
        assertEquals(y, actual.y, 1e-6)
        assertEquals(width, actual.width, 1e-6)
        assertEquals(height, actual.height, 1e-6)
    }
}
