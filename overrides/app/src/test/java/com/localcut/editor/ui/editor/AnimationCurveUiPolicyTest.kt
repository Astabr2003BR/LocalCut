package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CubicBezierCurve
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationCurveUiPolicyTest {
    private val source = listOf(
        PropertyAnimation(
            propertyId = "transform.x",
            keyframes = listOf(
                PropertyKeyframe("a", 0L, PropertyValue.Scalar(0.0)),
                PropertyKeyframe("b", 1_000_000L, PropertyValue.Scalar(100.0)),
            ),
        ),
    )

    @Test
    fun cubicBezierAlwaysCarriesValidControlPoints() {
        val curve = CubicBezierCurve(0.42, 0.0, 0.58, 1.0)
        val result = AnimationCurveUiPolicy.applyCubicBezier(source, curve)

        assertTrue(result.flatMap { it.keyframes }.all { it.easingToNext.kind == EasingKind.CUBIC_BEZIER })
        assertTrue(result.flatMap { it.keyframes }.all { it.easingToNext.bezier == curve })
        assertTrue(result.flatMap { it.validationIssues() }.isEmpty())
    }

    @Test
    fun stepsPresetUsesRequestedPositiveStepCount() {
        val result = AnimationCurveUiPolicy.applySteps(source, 6)

        assertTrue(result.flatMap { it.keyframes }.all { it.easingToNext.kind == EasingKind.STEPS })
        assertTrue(result.flatMap { it.keyframes }.all { it.easingToNext.steps == 6 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun stepsRejectsZero() {
        AnimationCurveUiPolicy.applySteps(source, 0)
    }

    @Test
    fun standardEasingPreservesKeyframeValueAndTiming() {
        val result = AnimationCurveUiPolicy.applyKind(source, EasingKind.EASE_OUT)

        assertEquals(source.single().keyframes.map { it.offsetUs }, result.single().keyframes.map { it.offsetUs })
        assertEquals(source.single().keyframes.map { it.value }, result.single().keyframes.map { it.value })
        assertTrue(result.single().keyframes.all { it.easingToNext.kind == EasingKind.EASE_OUT })
    }
}
