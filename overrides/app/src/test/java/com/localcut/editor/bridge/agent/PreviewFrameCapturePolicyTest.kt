package com.localcut.editor.bridge.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewFrameCapturePolicyTest {
    @Test
    fun landscapePreviewIsClampedToMaximumEdgeWithoutChangingAspectRatio() {
        val size = PreviewFrameCapturePolicy.targetSize(1920, 1080)

        assertEquals(720, size.width)
        assertEquals(405, size.height)
    }

    @Test
    fun portraitPreviewIsClampedToMaximumEdgeWithoutChangingAspectRatio() {
        val size = PreviewFrameCapturePolicy.targetSize(1080, 1920)

        assertEquals(405, size.width)
        assertEquals(720, size.height)
    }

    @Test
    fun smallPreviewIsNotUpscaled() {
        val size = PreviewFrameCapturePolicy.targetSize(320, 180)

        assertEquals(320, size.width)
        assertEquals(180, size.height)
    }
}
