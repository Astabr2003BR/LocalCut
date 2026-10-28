package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformValue
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
    fun stabilizationIsRelativeToExistingClipTransform() {
        val base = TransformValue(x = 100.0, y = -20.0, scale = 1.5, rotationDeg = 10.0, opacity = 0.7)
        val animations = GlobalMotionStabilizationPlanner.plan(
            samples = listOf(GlobalMotionSample(100_000L, 4.0, 2.0, 3.0, 1.0, confidence = 1.0)),
            strength = 1.0,
            baseTransform = base,
        )
        fun values(property: String) = animations.first { it.propertyId == property }.keyframes.map {
            (it.value as PropertyValue.Scalar).value
        }

        assertEquals(listOf(100.0, 96.0), values("transform.x"))
        assertEquals(listOf(-20.0, -22.0), values("transform.y"))
        assertEquals(listOf(10.0, 7.0), values("transform.rotationDeg"))
        assertEquals(listOf(1.5, 1.5), values("transform.scale"))
    }

    @Test
    fun edgeCompensationMultipliesBaseScaleAndFailsClosedBeyondSafetyLimit() {
        val animations = GlobalMotionStabilizationPlanner.plan(
            samples = listOf(GlobalMotionSample(100_000L, 54.0, 0.0, 0.0, 1.0, confidence = 1.0)),
            baseTransform = TransformValue(scale = 1.2),
            canvasWidth = 1080,
            canvasHeight = 1920,
            maxEdgeCompensationScale = 2.0,
        )
        val scale = animations.first { it.propertyId == "transform.scale" }
        assertEquals(1.2, (scale.keyframes.first().value as PropertyValue.Scalar).value, 1e-9)
        assertEquals(1.32, (scale.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)

        val excessive = runCatching {
            GlobalMotionStabilizationPlanner.plan(
                samples = listOf(GlobalMotionSample(100_000L, 600.0, 0.0, 0.0, 1.0, confidence = 1.0)),
                canvasWidth = 1080,
                canvasHeight = 1920,
                maxEdgeCompensationScale = 2.0,
            )
        }
        assertTrue(excessive.isFailure)
        assertEquals("STABILIZATION_EDGE_COMPENSATION_TOO_LARGE", excessive.exceptionOrNull()?.message)
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
