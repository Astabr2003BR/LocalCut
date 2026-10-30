package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCutMotionBlurEffectTest {
    @Test
    fun zeroShutterOrSingleSampleIsNoOp() {
        assertTrue(LocalCutMotionBlurEffect(0.0, 8).isNoOp(1920, 1080))
        assertTrue(LocalCutMotionBlurEffect(180.0, 1).isNoOp(1920, 1080))
        assertFalse(LocalCutMotionBlurEffect(180.0, 8).isNoOp(1920, 1080))
    }

    @Test
    fun invalidConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) { LocalCutMotionBlurEffect(-1.0, 8) }
        assertThrows(IllegalArgumentException::class.java) { LocalCutMotionBlurEffect(361.0, 8) }
        assertThrows(IllegalArgumentException::class.java) { LocalCutMotionBlurEffect(Double.NaN, 8) }
        assertThrows(IllegalArgumentException::class.java) { LocalCutMotionBlurEffect(180.0, 0) }
        assertThrows(IllegalArgumentException::class.java) { LocalCutMotionBlurEffect(180.0, 65) }
    }
}
