package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalMotionStabilizationPlannerTest {
    @Test
    fun integratesRelativeMotionIntoInverseTransformAnimations() {
        val animations = GlobalMotionStabilizationPlanner.plan(
            samples = listOf(
                GlobalMotionSample(100_000L, dx = 2.0, dy = 1.0, rotationDeg = 1.0, scale = 1.01, confidence = 1.0),
                GlobalMotionSample(200_000L, dx = 3.0, dy = -1.0, rotationDeg = 2.0, scale = 1.02, confidence = 1.0),
            ),
            strength = 1.0,
        )

        assertEquals(
            listOf("transform.x", "transform.y", "transform.rotationDeg", "transform.scale"),
            animations.map { it.propertyId },
        )
        val x = animations.first { it.propertyId == "transform.x" }
        val y = animations.first { it.propertyId == "transform.y" }
        val rotation = animations.first { it.propertyId == "transform.rotationDeg" }
        val scale = animations.first { it.propertyId == "transform.scale" }

        assertEquals(listOf(0L, 100_000L, 200_000L), x.keyframes.map { it.offsetUs })
        assertEquals(PropertyValue.Scalar(-2.0), x.keyframes[1].value)
        assertEquals(PropertyValue.Scalar(-5.0), x.keyframes[2].value)
        assertEquals(PropertyValue.Scalar(-1.0), y.keyframes[1].value)
        assertEquals(PropertyValue.Scalar(0.0), y.keyframes[2].value)
        assertEquals(PropertyValue.Scalar(-3.0), rotation.keyframes[2].value)
        val expectedScale = 1.0 / (1.01 * 1.02)
        assertEquals(expectedScale, (scale.keyframes[2].value as PropertyValue.Scalar).value, 1e-9)
    }

    @Test
    fun lowConfidenceSamplesHoldPreviousCorrection() {
        val animations = GlobalMotionStabilizationPlanner.plan(
            samples = listOf(
                GlobalMotionSample(100_000L, 4.0, 0.0, 0.0, 1.0, confidence = 0.9),
                GlobalMotionSample(200_000L, 50.0, 0.0, 0.0, 1.0, confidence = 0.2),
            ),
            strength = 1.0,
            minConfidence = 0.5,
        )
        val x = animations.first { it.propertyId == "transform.x" }
        assertEquals(PropertyValue.Scalar(-4.0), x.keyframes[1].value)
        assertEquals(PropertyValue.Scalar(-4.0), x.keyframes[2].value)
    }

    @Test
    fun strengthBlendsTowardNeutralTransform() {
        val animations = GlobalMotionStabilizationPlanner.plan(
            samples = listOf(GlobalMotionSample(100_000L, 10.0, -4.0, 20.0, 1.21, confidence = 1.0)),
            strength = 0.5,
        )
        fun scalar(property: String) = (
            animations.first { it.propertyId == property }.keyframes.last().value as PropertyValue.Scalar
        ).value
        assertEquals(-5.0, scalar("transform.x"), 1e-9)
        assertEquals(2.0, scalar("transform.y"), 1e-9)
        assertEquals(-10.0, scalar("transform.rotationDeg"), 1e-9)
        assertEquals(1.0 + ((1.0 / 1.21) - 1.0) * 0.5, scalar("transform.scale"), 1e-9)
    }

    @Test
    fun invalidSamplesFailClosed() {
        assertTrue(runCatching {
            GlobalMotionStabilizationPlanner.plan(
                listOf(GlobalMotionSample(0L, 1.0, 1.0, 0.0, 1.0, 1.0)),
            )
        }.isFailure)
        assertTrue(runCatching {
            GlobalMotionStabilizationPlanner.plan(
                listOf(GlobalMotionSample(100L, Double.NaN, 1.0, 0.0, 1.0, 1.0)),
            )
        }.isFailure)
        assertTrue(runCatching {
            GlobalMotionStabilizationPlanner.plan(emptyList(), strength = 1.1)
        }.isFailure)
    }
}
