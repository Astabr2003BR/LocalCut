package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3CompositingSupportPolicyTest {
    @Test
    fun neutralCompositingRemainsSupported() {
        assertTrue(Media3CompositionSupportPolicy.validate(plan(CompositingSettings()), RenderMode.EXPORT).isSuccess)
    }

    @Test
    fun everyDeclaredBlendModeIsSupportedBySharedPixelCompositor() {
        BlendMode.values().forEach { blendMode ->
            val result = Media3CompositionSupportPolicy.validate(
                plan(CompositingSettings(blendMode = blendMode)),
                RenderMode.EXPORT,
            )
            assertTrue("mode=$blendMode failure=${result.exceptionOrNull()?.message}", result.isSuccess)
        }
    }

    private fun plan(compositing: CompositingSettings): RenderPlan = RenderPlan(
        width = 1080,
        height = 1920,
        transformSpaceWidth = 1080,
        transformSpaceHeight = 1920,
        fps = 30,
        durationUs = 1_000_000L,
        visualLayers = listOf(
            RenderSequencePlan(
                type = TrackType.VISUAL,
                segments = listOf(
                    RenderSegment.Media(
                        clipId = "clip",
                        assetId = "asset",
                        assetType = AssetType.VIDEO,
                        uri = "content://video",
                        sourceMediaDurationUs = 1_000_000L,
                        durationUs = 1_000_000L,
                        sourceInUs = 0L,
                        sourceOutUs = 1_000_000L,
                        transform = TransformValue(),
                        opacity = 1.0,
                        volume = 1.0,
                        keyframes = emptyList(),
                        imageFrameRate = null,
                        compositing = compositing,
                    ),
                ),
            ),
        ),
        audioLayers = emptyList(),
    )
}
