package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.ChromaKeySettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskPoint
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Test

class BlendCompositorRoutePolicyTest {
    @Test
    fun normalOnlyCompositionKeepsDefaultMedia3Path() {
        val sequences = listOf(sequence(CompositingSettings()))

        assertEquals(
            BlendCompositorRoute.DEFAULT_MEDIA3,
            BlendCompositorRoutePolicy.routeFor(sequences),
        )
    }

    @Test
    fun anyNonNormalBlendRequiresLocalCutMultiTexturePath() {
        val sequences = listOf(
            sequence(CompositingSettings()),
            sequence(CompositingSettings(blendMode = BlendMode.MULTIPLY)),
        )

        assertEquals(
            BlendCompositorRoute.LOCALCUT_MULTI_TEXTURE,
            BlendCompositorRoutePolicy.routeFor(sequences),
        )
    }

    @Test
    fun alphaProducingMaskRequiresLocalCutCompositorEvenWithNormalBlend() {
        val settings = CompositingSettings(
            masks = listOf(
                MaskSpec(
                    id = "diamond",
                    kind = MaskKind.FREEFORM,
                    points = listOf(
                        MaskPoint(0.5, 0.1),
                        MaskPoint(0.9, 0.5),
                        MaskPoint(0.5, 0.9),
                        MaskPoint(0.1, 0.5),
                    ),
                ),
            ),
        )

        assertEquals(
            BlendCompositorRoute.LOCALCUT_MULTI_TEXTURE,
            BlendCompositorRoutePolicy.routeFor(listOf(sequence(settings))),
        )
    }

    @Test
    fun chromaKeyRequiresLocalCutCompositorEvenWithNormalBlend() {
        val settings = CompositingSettings(
            chromaKey = ChromaKeySettings(enabled = true),
        )

        assertEquals(
            BlendCompositorRoute.LOCALCUT_MULTI_TEXTURE,
            BlendCompositorRoutePolicy.routeFor(listOf(sequence(settings))),
        )
    }

    @Test
    fun emptyVisualInputDoesNotEnableCustomCompositor() {
        assertEquals(
            BlendCompositorRoute.DEFAULT_MEDIA3,
            BlendCompositorRoutePolicy.routeFor(emptyList()),
        )
    }

    private fun sequence(settings: CompositingSettings): Media3SequenceBlueprint = Media3SequenceBlueprint(
        type = TrackType.VISUAL,
        items = listOf(
            Media3BlueprintItem.Media(
                clipId = "clip-${settings.blendMode}",
                assetId = "asset-${settings.blendMode}",
                assetType = AssetType.VIDEO,
                uri = "content://${settings.blendMode}",
                outputTracks = Media3OutputTracks.VIDEO_ONLY,
                sourceMediaDurationUs = 1_000_000L,
                presentationDurationUs = 1_000_000L,
                sourceInUs = 0L,
                sourceOutUs = 1_000_000L,
                transform = TransformValue(),
                opacity = 1.0,
                volume = 1.0,
                keyframes = emptyList(),
                imageFrameRate = null,
                compositing = settings,
            ),
        ),
    )
}
