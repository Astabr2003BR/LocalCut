package com.localcut.editor.media

import com.localcut.editor.domain.model.TextAnimationDescriptor
import com.localcut.editor.domain.model.TextAnimationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAdvancedAnimationRenderTest {
    @Test
    fun typewriterPerCharacterDelayControlsVisibleText() {
        val state = TextOverlayMotionEvaluator.evaluate(
            text = "ABCDE",
            startUs = 0L,
            endUs = 2_000_000L,
            presentationTimeUs = 399_999L,
            entrance = TextAnimationDescriptor(
                kind = TextAnimationKind.TYPEWRITER,
                durationUs = 1_000_000L,
            ),
            perCharacterDelayUs = 200_000L,
        )

        assertEquals("AB", state.visibleText)
    }

    @Test
    fun slideAmountChangesRenderedMotionMagnitude() {
        val normal = TextOverlayMotionEvaluator.evaluate(
            text = "LocalCut",
            startUs = 0L,
            endUs = 2_000_000L,
            presentationTimeUs = 0L,
            entrance = TextAnimationDescriptor(
                kind = TextAnimationKind.SLIDE_UP,
                durationUs = 1_000_000L,
                amount = 1.0,
            ),
        )
        val strong = TextOverlayMotionEvaluator.evaluate(
            text = "LocalCut",
            startUs = 0L,
            endUs = 2_000_000L,
            presentationTimeUs = 0L,
            entrance = TextAnimationDescriptor(
                kind = TextAnimationKind.SLIDE_UP,
                durationUs = 1_000_000L,
                amount = 2.0,
            ),
        )

        assertTrue(kotlin.math.abs(strong.anchorY) > kotlin.math.abs(normal.anchorY))
        assertEquals(-0.2f, normal.anchorY, 0.0001f)
        assertEquals(-0.4f, strong.anchorY, 0.0001f)
    }

    @Test
    fun loopWiggleAmountChangesRenderedRotation() {
        val state = TextOverlayMotionEvaluator.evaluate(
            text = "LocalCut",
            startUs = 0L,
            endUs = 2_000_000L,
            presentationTimeUs = 250_000L,
            loop = TextAnimationDescriptor(
                kind = TextAnimationKind.WIGGLE,
                durationUs = 1_000_000L,
                amount = 2.0,
            ),
        )

        assertEquals(12.0f, state.rotationOffsetDeg, 0.0001f)
    }
}
