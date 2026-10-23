package com.localcut.editor.media

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.TextAnimationDescriptor
import com.localcut.editor.domain.model.TextAnimationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayMotionEvaluatorTest {
    @Test
    fun fadeEntranceMovesAlphaFromZeroToBaseOpacity() {
        val state0 = TextOverlayMotionEvaluator.evaluate(
            text = "LocalCut",
            startUs = 1_000_000L,
            endUs = 4_000_000L,
            presentationTimeUs = 1_000_000L,
            baseOpacity = 0.8f,
            entrance = TextAnimationDescriptor(TextAnimationKind.FADE, 1_000_000L, Easing(EasingKind.LINEAR)),
        )
        val state1 = TextOverlayMotionEvaluator.evaluate(
            text = "LocalCut",
            startUs = 1_000_000L,
            endUs = 4_000_000L,
            presentationTimeUs = 2_000_000L,
            baseOpacity = 0.8f,
            entrance = TextAnimationDescriptor(TextAnimationKind.FADE, 1_000_000L, Easing(EasingKind.LINEAR)),
        )

        assertEquals(0f, state0.alpha, 0.0001f)
        assertEquals(0.8f, state1.alpha, 0.0001f)
    }

    @Test
    fun slideUpStartsBelowAndSettlesAtBaseAnchor() {
        val start = TextOverlayMotionEvaluator.evaluate(
            text = "A",
            startUs = 0L,
            endUs = 2_000_000L,
            presentationTimeUs = 0L,
            baseAnchorX = 0.1f,
            baseAnchorY = -0.2f,
            entrance = TextAnimationDescriptor(TextAnimationKind.SLIDE_UP, 1_000_000L, amount = 1.0),
        )
        val settled = TextOverlayMotionEvaluator.evaluate(
            text = "A",
            startUs = 0L,
            endUs = 2_000_000L,
            presentationTimeUs = 1_000_000L,
            baseAnchorX = 0.1f,
            baseAnchorY = -0.2f,
            entrance = TextAnimationDescriptor(TextAnimationKind.SLIDE_UP, 1_000_000L, amount = 1.0),
        )

        assertTrue(start.anchorY < settled.anchorY)
        assertEquals(-0.2f, settled.anchorY, 0.0001f)
        assertEquals(0.1f, settled.anchorX, 0.0001f)
    }

    @Test
    fun typewriterRevealsCharactersProgressively() {
        val state = TextOverlayMotionEvaluator.evaluate(
            text = "ABCDE",
            startUs = 0L,
            endUs = 2_000_000L,
            presentationTimeUs = 500_000L,
            entrance = TextAnimationDescriptor(TextAnimationKind.TYPEWRITER, 1_000_000L, Easing(EasingKind.LINEAR)),
        )

        assertTrue(state.visibleText.length in 2..3)
        assertTrue("ABCDE".startsWith(state.visibleText))
    }

    @Test
    fun wiggleLoopIsDeterministicAndReturnsDifferentRotationAcrossPhase() {
        val loop = TextAnimationDescriptor(TextAnimationKind.WIGGLE, 1_000_000L, amount = 1.0)
        val a = TextOverlayMotionEvaluator.evaluate("A", 0L, 3_000_000L, 125_000L, loop = loop)
        val same = TextOverlayMotionEvaluator.evaluate("A", 0L, 3_000_000L, 125_000L, loop = loop)
        val b = TextOverlayMotionEvaluator.evaluate("A", 0L, 3_000_000L, 375_000L, loop = loop)

        assertEquals(a.rotationOffsetDeg, same.rotationOffsetDeg, 0.0001f)
        assertTrue(kotlin.math.abs(a.rotationOffsetDeg - b.rotationOffsetDeg) > 0.1f)
    }
}
