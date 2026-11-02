package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPathPlannerTest {
    @Test
    fun pathPointsBecomeSynchronizedXYPropertyAnimations() {
        val points = listOf(
            MotionPathPoint("p0", 0L, x = 10.0, y = 20.0, easingToNext = Easing(EasingKind.EASE_IN)),
            MotionPathPoint("p1", 500_000L, x = 110.0, y = 60.0, easingToNext = Easing(EasingKind.EASE_OUT)),
            MotionPathPoint("p2", 1_000_000L, x = 210.0, y = -40.0),
        )

        val animations = MotionPathPlanner.plan(points)

        assertEquals(listOf("transform.x", "transform.y"), animations.map { it.propertyId })
        val x = animations[0]
        val y = animations[1]
        assertEquals(listOf(0L, 500_000L, 1_000_000L), x.keyframes.map { it.offsetUs })
        assertEquals(x.keyframes.map { it.offsetUs }, y.keyframes.map { it.offsetUs })
        assertEquals(PropertyValue.Scalar(10.0), x.keyframes[0].value)
        assertEquals(PropertyValue.Scalar(110.0), x.keyframes[1].value)
        assertEquals(PropertyValue.Scalar(210.0), x.keyframes[2].value)
        assertEquals(PropertyValue.Scalar(20.0), y.keyframes[0].value)
        assertEquals(PropertyValue.Scalar(60.0), y.keyframes[1].value)
        assertEquals(PropertyValue.Scalar(-40.0), y.keyframes[2].value)
        assertEquals(EasingKind.EASE_IN, x.keyframes[0].easingToNext.kind)
        assertEquals(EasingKind.EASE_OUT, y.keyframes[1].easingToNext.kind)
    }

    @Test
    fun invalidPointCountIdsOffsetsValuesOrEasingFailClosed() {
        assertTrue(runCatching { MotionPathPlanner.plan(emptyList()) }.isFailure)
        assertTrue(runCatching {
            MotionPathPlanner.plan(listOf(MotionPathPoint("only", 0L, 0.0, 0.0)))
        }.isFailure)
        assertTrue(runCatching {
            MotionPathPlanner.plan(
                listOf(
                    MotionPathPoint("same", 0L, 0.0, 0.0),
                    MotionPathPoint("same", 1L, 1.0, 1.0),
                ),
            )
        }.isFailure)
        assertTrue(runCatching {
            MotionPathPlanner.plan(
                listOf(
                    MotionPathPoint("a", 100L, 0.0, 0.0),
                    MotionPathPoint("b", 100L, 1.0, 1.0),
                ),
            )
        }.isFailure)
        assertTrue(runCatching {
            MotionPathPlanner.plan(
                listOf(
                    MotionPathPoint("a", 0L, Double.NaN, 0.0),
                    MotionPathPoint("b", 1L, 1.0, 1.0),
                ),
            )
        }.isFailure)
        assertTrue(runCatching {
            MotionPathPlanner.plan(
                listOf(
                    MotionPathPoint("a", 0L, 0.0, 0.0, Easing(EasingKind.STEPS, steps = 0)),
                    MotionPathPoint("b", 1L, 1.0, 1.0),
                ),
            )
        }.isFailure)
    }

    @Test
    fun pointAndDurationBoundsAreEnforced() {
        val tooMany = (0..MotionPathPlanner.MAX_POINTS).map { index ->
            MotionPathPoint("p$index", index.toLong(), index.toDouble(), 0.0)
        }
        assertTrue(runCatching { MotionPathPlanner.plan(tooMany) }.isFailure)

        val tooLong = listOf(
            MotionPathPoint("start", 0L, 0.0, 0.0),
            MotionPathPoint("end", MotionPathPlanner.MAX_OFFSET_US + 1L, 1.0, 1.0),
        )
        assertTrue(runCatching { MotionPathPlanner.plan(tooLong) }.isFailure)
    }
}
