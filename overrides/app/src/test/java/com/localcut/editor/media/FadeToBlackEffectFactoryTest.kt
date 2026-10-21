package com.localcut.editor.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FadeToBlackEffectFactoryTest {
    @Test
    fun noFadeSpecProducesNoEffect() {
        assertNull(
            FadeToBlackEffectFactory.create(
                durationUs = 4_000_000L,
                spec = ClipTransitionRenderSpec("clip"),
            ),
        )
    }

    @Test
    fun rgbMatrixScalesColorChannelsButPreservesAlpha() {
        val effect = FadeToBlackEffectFactory.create(
            durationUs = 4_000_000L,
            spec = ClipTransitionRenderSpec(
                clipId = "clip",
                fadeInUs = 1_000_000L,
                fadeOutUs = 1_000_000L,
            ),
        )
        assertNotNull(effect)

        assertArrayEquals(
            floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 0.5f, 0f,
                0f, 0f, 0f, 1f,
            ),
            effect!!.getMatrix(500_000L, false),
            0.0001f,
        )
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
            effect.getMatrix(2_000_000L, false),
            0.0001f,
        )
    }
}
