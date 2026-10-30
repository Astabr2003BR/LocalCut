package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnionSkinColorPolicyTest {
    @Test
    fun originalModePreservesStrokeColor() {
        val original = 0xFF123456.toInt()
        assertEquals(original, OnionSkinColorPolicy.argbFor(-1, original, OnionSkinColorMode.ORIGINAL))
        assertEquals(original, OnionSkinColorPolicy.argbFor(1, original, OnionSkinColorMode.ORIGINAL))
    }

    @Test
    fun traditionalModeSeparatesPastAndFuture() {
        val original = 0xFF123456.toInt()
        assertEquals(OnionSkinColorPolicy.PREVIOUS_ARGB, OnionSkinColorPolicy.argbFor(-1, original, OnionSkinColorMode.TRADITIONAL))
        assertEquals(OnionSkinColorPolicy.NEXT_ARGB, OnionSkinColorPolicy.argbFor(1, original, OnionSkinColorMode.TRADITIONAL))
    }

    @Test
    fun currentFrameIsRejectedBecauseItIsNotAGhost() {
        assertTrue(
            runCatching {
                OnionSkinColorPolicy.argbFor(0, 0xFFFFFFFF.toInt(), OnionSkinColorMode.TRADITIONAL)
            }.isFailure,
        )
    }
}
