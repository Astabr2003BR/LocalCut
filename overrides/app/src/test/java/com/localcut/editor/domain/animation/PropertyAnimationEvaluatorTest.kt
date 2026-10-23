package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.CubicBezierCurve
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyAnimationEvaluatorTest {
    @Test
    fun holdKeepsLeftValueUntilNextKeyframe() {
        val animation = scalarAnimation(Easing(EasingKind.HOLD))

        assertEquals(0.0, scalar(PropertyAnimationEvaluator.valueAt(animation, 500_000L)), 0.0)
        assertEquals(10.0, scalar(PropertyAnimationEvaluator.valueAt(animation, 1_000_000L)), 0.0)
    }

    @Test
    fun linearInterpolatesScalarAndClampsOutsideRange() {
        val animation = scalarAnimation(Easing(EasingKind.LINEAR))

        assertEquals(0.0, scalar(PropertyAnimationEvaluator.valueAt(animation, -100L)), 0.0)
        assertEquals(5.0, scalar(PropertyAnimationEvaluator.valueAt(animation, 500_000L)), 1e-9)
        assertEquals(10.0, scalar(PropertyAnimationEvaluator.valueAt(animation, 2_000_000L)), 0.0)
    }

    @Test
    fun cubicBezierUsesTimingCurveAndStillHitsEndpoints() {
        val animation = scalarAnimation(
            Easing(
                kind = EasingKind.CUBIC_BEZIER,
                bezier = CubicBezierCurve(0.42, 0.0, 1.0, 1.0),
            ),
        )

        val middle = scalar(PropertyAnimationEvaluator.valueAt(animation, 500_000L))

        assertTrue(middle in 0.0..10.0)
        assertNotEquals(5.0, middle, 1e-3)
        assertEquals(0.0, scalar(PropertyAnimationEvaluator.valueAt(animation, 0L)), 0.0)
        assertEquals(10.0, scalar(PropertyAnimationEvaluator.valueAt(animation, 1_000_000L)), 0.0)
    }

    @Test
    fun stepsQuantizesProgress() {
        val animation = scalarAnimation(Easing(EasingKind.STEPS, steps = 4))

        assertEquals(2.5, scalar(PropertyAnimationEvaluator.valueAt(animation, 300_000L)), 1e-9)
        assertEquals(7.5, scalar(PropertyAnimationEvaluator.valueAt(animation, 990_000L)), 1e-9)
    }

    @Test
    fun seededRandomIsDeterministicAndBounded() {
        val animation = scalarAnimation(Easing(EasingKind.RANDOM_SEEDED, seed = 42L))

        val first = scalar(PropertyAnimationEvaluator.valueAt(animation, 500_000L))
        val second = scalar(PropertyAnimationEvaluator.valueAt(animation, 500_000L))

        assertEquals(first, second, 0.0)
        assertTrue(first in 0.0..10.0)
    }

    @Test
    fun pointAndColorInterpolateByType() {
        val pointAnimation = PropertyAnimation(
            "point",
            listOf(
                PropertyKeyframe("p0", 0L, PropertyValue.Point(0.0, 10.0)),
                PropertyKeyframe("p1", 1_000_000L, PropertyValue.Point(10.0, 20.0)),
            ),
        )
        val colorAnimation = PropertyAnimation(
            "color",
            listOf(
                PropertyKeyframe("c0", 0L, PropertyValue.Color(0xFF000000.toInt())),
                PropertyKeyframe("c1", 1_000_000L, PropertyValue.Color(0xFFFFFFFF.toInt())),
            ),
        )

        assertEquals(PropertyValue.Point(5.0, 15.0), PropertyAnimationEvaluator.valueAt(pointAnimation, 500_000L))
        assertEquals(0xFF808080.toInt(), (PropertyAnimationEvaluator.valueAt(colorAnimation, 500_000L) as PropertyValue.Color).argb)
    }

    @Test
    fun legacyTransformAdapterPreservesLinearInterpolation() {
        val keyframes = listOf(
            TransformKeyframe(0L, TransformValue(x = 0.0, y = 10.0, scale = 1.0, rotationDeg = 0.0, opacity = 1.0)),
            TransformKeyframe(1_000_000L, TransformValue(x = 10.0, y = 20.0, scale = 2.0, rotationDeg = 90.0, opacity = 0.5)),
        )

        val animations = LegacyTransformAnimationAdapter.fromLegacy(keyframes)
        val evaluated = LegacyTransformAnimationAdapter.valueAt(animations, 500_000L)

        assertEquals(5.0, evaluated.x, 1e-9)
        assertEquals(15.0, evaluated.y, 1e-9)
        assertEquals(1.5, evaluated.scale, 1e-9)
        assertEquals(45.0, evaluated.rotationDeg, 1e-9)
        assertEquals(0.75, evaluated.opacity, 1e-9)
    }

    private fun scalarAnimation(easing: Easing): PropertyAnimation = PropertyAnimation(
        propertyId = "scalar",
        keyframes = listOf(
            PropertyKeyframe("a", 0L, PropertyValue.Scalar(0.0), easing),
            PropertyKeyframe("b", 1_000_000L, PropertyValue.Scalar(10.0)),
        ),
    )

    private fun scalar(value: PropertyValue): Double = (value as PropertyValue.Scalar).value
}
