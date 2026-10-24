package com.localcut.editor.render

import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.ChromaKeySettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskPoint
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositingEvaluatorTest {
    @Test
    fun defaultSettingsEvaluateToNeutralCompositing() {
        val evaluated = CompositingEvaluator.valueAt(CompositingSettings(), 500_000L)

        assertEquals(BlendMode.NORMAL, evaluated.blendMode)
        assertTrue(evaluated.masks.isEmpty())
        assertFalse(evaluated.chromaKey.enabled)
        assertEquals(CropMode.NONE, evaluated.crop.mode)
    }

    @Test
    fun maskScalarAndPointAnimationsEvaluateAtRequestedTime() {
        val mask = MaskSpec(
            id = "mask",
            kind = MaskKind.FREEFORM,
            points = listOf(MaskPoint(0.0, 0.0), MaskPoint(1.0, 1.0)),
            opacity = 1.0,
            animations = listOf(
                PropertyAnimation(
                    "opacity",
                    listOf(
                        PropertyKeyframe("o0", 0L, PropertyValue.Scalar(0.0)),
                        PropertyKeyframe("o1", 1_000_000L, PropertyValue.Scalar(1.0)),
                    ),
                ),
                PropertyAnimation(
                    "point.1",
                    listOf(
                        PropertyKeyframe("p0", 0L, PropertyValue.Point(1.0, 1.0), Easing(EasingKind.LINEAR)),
                        PropertyKeyframe("p1", 1_000_000L, PropertyValue.Point(0.5, 0.25)),
                    ),
                ),
            ),
        )

        val evaluated = CompositingEvaluator.valueAt(
            CompositingSettings(blendMode = BlendMode.SCREEN, masks = listOf(mask)),
            500_000L,
        )

        assertEquals(BlendMode.SCREEN, evaluated.blendMode)
        assertEquals(0.5, evaluated.masks.single().opacity, 1e-9)
        assertEquals(MaskPoint(0.75, 0.625), evaluated.masks.single().points[1])
    }

    @Test
    fun unsupportedMaskAnimationPropertyIsRejectedInsteadOfIgnored() {
        val settings = CompositingSettings(
            masks = listOf(
                MaskSpec(
                    "mask",
                    MaskKind.RECTANGLE,
                    animations = listOf(
                        PropertyAnimation(
                            "unknown",
                            listOf(PropertyKeyframe("k", 0L, PropertyValue.Scalar(1.0))),
                        ),
                    ),
                ),
            ),
        )

        val failure = runCatching { CompositingEvaluator.valueAt(settings, 0L) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun chromaAndCropAreCarriedWithoutChangingValues() {
        val settings = CompositingSettings(
            chromaKey = ChromaKeySettings(true, 0xFF00FF00.toInt(), 0.25, 0.1, 0.2, 2.0),
            crop = CropSettings(CropMode.FILL, 0.1, 0.2, 0.9, 0.8),
        )

        val evaluated = CompositingEvaluator.valueAt(settings, 0L)

        assertEquals(settings.chromaKey, evaluated.chromaKey)
        assertEquals(settings.crop, evaluated.crop)
    }
}
