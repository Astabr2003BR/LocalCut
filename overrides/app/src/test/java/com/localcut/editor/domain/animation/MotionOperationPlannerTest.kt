package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionOperationPlannerTest {
    @Test
    fun moveScaleRotateAndOpacityUseExistingPropertyAnimationModel() {
        val easing = Easing(EasingKind.EASE_OUT)
        val specs = listOf(
            MotionOperationSpec("move-x", MotionOperationKind.MOVE_X, 100_000L, 500_000L, 0.0, 120.0, easing),
            MotionOperationSpec("scale", MotionOperationKind.SCALE, 0L, 250_000L, 0.8, 1.0, easing),
            MotionOperationSpec("rotate", MotionOperationKind.ROTATE, 0L, 300_000L, -15.0, 0.0, easing),
            MotionOperationSpec("opacity", MotionOperationKind.OPACITY, 0L, 200_000L, 0.0, 1.0, easing),
        )

        val animations = specs.map(MotionOperationPlanner::toAnimation)

        assertEquals(
            listOf("transform.x", "transform.scale", "transform.rotationDeg", "transform.opacity"),
            animations.map { it.propertyId },
        )
        assertEquals(listOf(100_000L, 600_000L), animations[0].keyframes.map { it.offsetUs })
        assertEquals(PropertyValue.Scalar(0.0), animations[0].keyframes.first().value)
        assertEquals(PropertyValue.Scalar(120.0), animations[0].keyframes.last().value)
        assertEquals(EasingKind.EASE_OUT, animations[0].keyframes.first().easingToNext.kind)
    }

    @Test
    fun moveYMapsToTransformY() {
        val animation = MotionOperationPlanner.toAnimation(
            MotionOperationSpec("move-y", MotionOperationKind.MOVE_Y, 0L, 100_000L, -10.0, 50.0),
        )
        assertEquals("transform.y", animation.propertyId)
    }

    @Test
    fun invalidSpecFailsClosed() {
        assertTrue(
            runCatching {
                MotionOperationPlanner.toAnimation(
                    MotionOperationSpec("", MotionOperationKind.MOVE_X, 0L, 100_000L, 0.0, 1.0),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                MotionOperationPlanner.toAnimation(
                    MotionOperationSpec("bad", MotionOperationKind.SCALE, 0L, 0L, 1.0, 2.0),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                MotionOperationPlanner.toAnimation(
                    MotionOperationSpec("nan", MotionOperationKind.OPACITY, 0L, 100_000L, Double.NaN, 1.0),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                MotionOperationPlanner.toAnimation(
                    MotionOperationSpec("overflow", MotionOperationKind.ROTATE, Long.MAX_VALUE, 1L, 0.0, 1.0),
                )
            }.isFailure,
        )
    }
}
