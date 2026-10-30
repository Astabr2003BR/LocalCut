package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaggeredAnimationPlannerTest {
    private fun animation(id: String): PropertyAnimation = PropertyAnimation(
        propertyId = "position.x",
        keyframes = listOf(
            PropertyKeyframe("$id-k1", 0L, PropertyValue.Scalar(0.0), Easing()),
            PropertyKeyframe("$id-k2", 500_000L, PropertyValue.Scalar(100.0), Easing()),
        ),
    )

    @Test
    fun forwardStaggerAddsDeterministicDelayToKeyframes() {
        val planned = StaggeredAnimationPlanner.plan(
            targets = listOf(
                AnimationStaggerTarget("a", listOf(animation("a"))),
                AnimationStaggerTarget("b", listOf(animation("b"))),
                AnimationStaggerTarget("c", listOf(animation("c"))),
            ),
            stepUs = 100_000L,
            order = AnimationStaggerOrder.FORWARD,
        )

        assertEquals(listOf(0L, 100_000L, 200_000L), planned.map { it.delayUs })
        assertEquals(listOf(0L, 500_000L), planned[0].animations.single().keyframes.map { it.offsetUs })
        assertEquals(listOf(100_000L, 600_000L), planned[1].animations.single().keyframes.map { it.offsetUs })
        assertEquals(listOf(200_000L, 700_000L), planned[2].animations.single().keyframes.map { it.offsetUs })
    }

    @Test
    fun reverseStaggerKeepsTargetOrderButReversesDelayOrder() {
        val planned = StaggeredAnimationPlanner.plan(
            targets = listOf(
                AnimationStaggerTarget("a", listOf(animation("a"))),
                AnimationStaggerTarget("b", listOf(animation("b"))),
                AnimationStaggerTarget("c", listOf(animation("c"))),
            ),
            stepUs = 100_000L,
            order = AnimationStaggerOrder.REVERSE,
        )

        assertEquals(listOf("a", "b", "c"), planned.map { it.targetId })
        assertEquals(listOf(200_000L, 100_000L, 0L), planned.map { it.delayUs })
    }

    @Test
    fun invalidTargetsAndOverflowFailClosed() {
        assertTrue(
            runCatching {
                StaggeredAnimationPlanner.plan(
                    targets = listOf(
                        AnimationStaggerTarget("same", listOf(animation("a"))),
                        AnimationStaggerTarget("same", listOf(animation("b"))),
                    ),
                    stepUs = 10L,
                )
            }.isFailure,
        )
        assertTrue(runCatching { StaggeredAnimationPlanner.plan(emptyList(), -1L) }.isFailure)
        assertTrue(
            runCatching {
                StaggeredAnimationPlanner.plan(
                    targets = listOf(
                        AnimationStaggerTarget("safe", listOf(animation("safe"))),
                        AnimationStaggerTarget(
                            "overflow",
                            listOf(
                                PropertyAnimation(
                                    propertyId = "opacity",
                                    keyframes = listOf(
                                        PropertyKeyframe("k", Long.MAX_VALUE, PropertyValue.Scalar(1.0)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    stepUs = 1L,
                    order = AnimationStaggerOrder.FORWARD,
                )
            }.isFailure,
        )
    }
}
