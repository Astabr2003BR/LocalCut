package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Test

class BlendCompositorRoutePolicyTest {
    @Test
    fun normalOnlyCompositionKeepsDefaultMedia3Path() {
        val sequences = listOf(sequence(BlendMode.NORMAL))

        assertEquals(
            BlendCompositorRoute.DEFAULT_MEDIA3,
            BlendCompositorRoutePolicy.routeFor(sequences),
        )
    }

    @Test
    fun anyNonNormalBlendRequiresLocalCutMultiTexturePath() {
        val sequences = listOf(
            sequence(BlendMode.NORMAL),
            sequence(BlendMode.MULTIPLY),
        )

        assertEquals(
            BlendCompositorRoute.LOCALCUT_MULTI_TEXTURE,
            BlendCompositorRoutePolicy.routeFor(sequences),
        )
    }

    @Test
    fun emptyVisualInputDoesNotEnableCustomCompositor() {
        assertEquals(
            BlendCompositorRoute.DEFAULT_MEDIA3,
            BlendCompositorRoutePolicy.routeFor(emptyList()),
        )
    }

    private fun sequence(mode: BlendMode): Media3SequenceBlueprint = Media3SequenceBlueprint(
        type = TrackType.VISUAL,
        items = listOf(
            Media3BlueprintItem.Media(
                clipId = "clip-$mode",
                assetId = "asset-$mode",
                assetType = AssetType.VIDEO,
                uri = "content://$mode",
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
                compositing = CompositingSettings(blendMode = mode),
            ),
        ),
    )
}
