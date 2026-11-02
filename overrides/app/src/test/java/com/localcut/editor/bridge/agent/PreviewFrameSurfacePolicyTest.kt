package com.localcut.editor.bridge.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewFrameSurfacePolicyTest {
    @Test
    fun invalidSurfaceIsNeverEligibleForPixelCopy() {
        assertFalse(
            PreviewFrameSurfacePolicy.canCaptureSurfaceView(
                width = 640,
                height = 360,
                surfaceValid = false,
            ),
        )
    }

    @Test
    fun validSurfaceWithPositiveDimensionsIsEligibleForPixelCopy() {
        assertTrue(
            PreviewFrameSurfacePolicy.canCaptureSurfaceView(
                width = 640,
                height = 360,
                surfaceValid = true,
            ),
        )
    }

    @Test
    fun zeroSizedSurfaceIsNotEligibleForPixelCopy() {
        assertFalse(
            PreviewFrameSurfacePolicy.canCaptureSurfaceView(
                width = 0,
                height = 360,
                surfaceValid = true,
            ),
        )
    }
}
