package com.localcut.editor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeSuiteModelTest {
    @Test
    fun legacyClipDefaultsKeepCurrentVisualAndTimingBehavior() {
        val clip = Clip(
            id = "clip-1",
            assetId = "asset-1",
            startUs = 0L,
            durationUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
        )

        assertEquals(BlendMode.NORMAL, clip.compositing.blendMode)
        assertTrue(clip.compositing.masks.isEmpty())
        assertFalse(clip.compositing.chromaKey.enabled)
        assertTrue(clip.effects.isEmpty())
        assertTrue(clip.propertyAnimations.isEmpty())
        assertEquals(1.0, clip.timeMap.baseSpeed, 0.0)
        assertFalse(clip.timeMap.reverse)
        assertTrue(clip.timeMap.speedPoints.isEmpty())
        assertTrue(clip.timeMap.freezeSegments.isEmpty())
    }

    @Test
    fun propertyAnimationValidationRejectsDuplicateOffsetsAndNonFiniteValues() {
        val animation = PropertyAnimation(
            propertyId = "transform.x",
            keyframes = listOf(
                PropertyKeyframe("k1", 100L, PropertyValue.Scalar(1.0)),
                PropertyKeyframe("k2", 100L, PropertyValue.Scalar(Double.NaN)),
            ),
        )

        val issues = animation.validationIssues()

        assertTrue(issues.any { "strictly increasing" in it })
        assertTrue(issues.any { "finite" in it })
    }

    @Test
    fun maskValidationRejectsInvalidGeometryAndNegativeFeather() {
        val mask = MaskSpec(
            id = "mask-1",
            kind = MaskKind.FREEFORM,
            points = listOf(MaskPoint(0.0, 0.0), MaskPoint(Double.POSITIVE_INFINITY, 1.0)),
            featherPx = -1.0,
        )

        val issues = mask.validationIssues()

        assertTrue(issues.any { "finite" in it })
        assertTrue(issues.any { "feather" in it })
    }

    @Test
    fun effectParametersHaveStableSortedOrderAndRejectNonFiniteNumbers() {
        val effect = EffectInstance(
            id = "fx-1",
            typeId = "localcut.color.basic",
            parameters = mapOf(
                "z" to EffectParameter.Number(2.0),
                "a" to EffectParameter.Number(Double.NEGATIVE_INFINITY),
            ),
        )

        assertEquals(listOf("a", "z"), effect.orderedParameters().map { it.first })
        assertTrue(effect.validationIssues().any { "finite" in it })
    }

    @Test
    fun timeMapValidationRejectsInvalidSpeedAndUnorderedPoints() {
        val timeMap = TimeMap(
            baseSpeed = 0.0,
            speedPoints = listOf(
                SpeedPoint(offsetUs = 200L, speed = 1.0),
                SpeedPoint(offsetUs = 100L, speed = -1.0),
            ),
            freezeSegments = listOf(FreezeSegment(startOffsetUs = -1L, durationUs = 10L, sourceOffsetUs = 0L)),
        )

        val issues = timeMap.validationIssues()

        assertTrue(issues.any { "baseSpeed" in it })
        assertTrue(issues.any { "strictly increasing" in it })
        assertTrue(issues.any { "speed" in it })
        assertTrue(issues.any { "freeze" in it })
    }
}
