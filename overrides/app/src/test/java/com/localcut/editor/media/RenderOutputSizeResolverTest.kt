package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderOutputSizeResolverTest {
    @Test
    fun fastPreviewDownscalesPortraitProjectUsingSameRoundedDimensionsAsRender() {
        val size = RenderOutputSizeResolver.resolve(
            projectWidth = 1080,
            projectHeight = 1920,
            mode = RenderMode.FAST_PREVIEW,
            outputSettings = RenderOutputSettings(previewMaxLongEdge = 540),
        )

        assertEquals(RenderOutputSize(304, 540), size)
    }

    @Test
    fun fastPreviewKeepsDimensionsWhenAlreadyWithinBudget() {
        val size = RenderOutputSizeResolver.resolve(
            projectWidth = 320,
            projectHeight = 240,
            mode = RenderMode.FAST_PREVIEW,
            outputSettings = RenderOutputSettings(previewMaxLongEdge = 540),
        )

        assertEquals(RenderOutputSize(320, 240), size)
    }

    @Test
    fun fullPreviewAndExportHonorExplicitOutputSizeWithoutPreviewDownscale() {
        val settings = RenderOutputSettings(width = 1280, height = 720, previewMaxLongEdge = 360)

        assertEquals(
            RenderOutputSize(1280, 720),
            RenderOutputSizeResolver.resolve(1920, 1080, RenderMode.FULL_PREVIEW, settings),
        )
        assertEquals(
            RenderOutputSize(1280, 720),
            RenderOutputSizeResolver.resolve(1920, 1080, RenderMode.EXPORT, settings),
        )
    }

    @Test
    fun defaultFastPreviewUses720LongEdge() {
        val size = RenderOutputSizeResolver.resolve(
            projectWidth = 1920,
            projectHeight = 1080,
            mode = RenderMode.FAST_PREVIEW,
            outputSettings = RenderOutputSettings(),
        )

        assertEquals(RenderOutputSize(720, 405), size)
    }
}
