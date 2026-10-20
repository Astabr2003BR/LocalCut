package com.localcut.editor.media

import androidx.media3.effect.FrameDropEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3FrameRatePolicyTest {
    @Test
    fun unchangedFpsNeedsNoFrameDropEffect() {
        assertTrue(Media3FrameRatePolicy.effectsFor(30, 30).isEmpty())
    }

    @Test
    fun lowerTargetFpsAddsExactlyOneFrameDropEffect() {
        val effects = Media3FrameRatePolicy.effectsFor(60, 30)

        assertEquals(1, effects.size)
        assertTrue(effects.single() is FrameDropEffect)
    }

    @Test
    fun higherTargetFpsIsRejectedBecauseInterpolationIsNotImplemented() {
        val result = runCatching { Media3FrameRatePolicy.effectsFor(30, 60) }

        assertTrue(result.isFailure)
    }
}
