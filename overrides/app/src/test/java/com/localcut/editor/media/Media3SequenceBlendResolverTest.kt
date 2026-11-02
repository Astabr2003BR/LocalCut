package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3SequenceBlendResolverTest {
    @Test
    fun resolvesBlendModeFromActiveMediaItemUsingPresentationTime() {
        val sequence = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(
                media("first", 1_000_000L, BlendMode.MULTIPLY),
                media("second", 2_000_000L, BlendMode.SCREEN),
            ),
        )

        assertEquals(BlendMode.MULTIPLY, Media3SequenceBlendResolver.blendModeAt(sequence, 500_000L))
        assertEquals(BlendMode.SCREEN, Media3SequenceBlendResolver.blendModeAt(sequence, 1_500_000L))
        assertEquals(BlendMode.SCREEN, Media3SequenceBlendResolver.blendModeAt(sequence, 2_999_999L))
    }

    @Test
    fun gapsNegativeTimeAndTimeAfterSequenceResolveToNormal() {
        val sequence = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(
                Media3BlueprintItem.Gap(500_000L),
                media("clip", 1_000_000L, BlendMode.OVERLAY),
                Media3BlueprintItem.Gap(500_000L),
            ),
        )

        assertEquals(BlendMode.NORMAL, Media3SequenceBlendResolver.blendModeAt(sequence, -1L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendResolver.blendModeAt(sequence, 250_000L))
        assertEquals(BlendMode.OVERLAY, Media3SequenceBlendResolver.blendModeAt(sequence, 750_000L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendResolver.blendModeAt(sequence, 1_750_000L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendResolver.blendModeAt(sequence, 3_000_000L))
    }

    @Test
    fun pixelCompositorRequirementOnlyActivatesForNonNormalVisualMedia() {
        val normal = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(media("normal", 1_000_000L, BlendMode.NORMAL)),
        )
        val multiply = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(media("multiply", 1_000_000L, BlendMode.MULTIPLY)),
        )
        val screen = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(media("screen", 1_000_000L, BlendMode.SCREEN)),
        )

        assertFalse(Media3SequenceBlendResolver.requiresPixelCompositor(listOf(normal)))
        assertTrue(Media3SequenceBlendResolver.requiresPixelCompositor(listOf(normal, multiply)))
        assertTrue(Media3SequenceBlendResolver.requiresPixelCompositor(listOf(screen)))
        assertEquals(
            linkedSetOf(BlendMode.MULTIPLY, BlendMode.SCREEN),
            Media3SequenceBlendResolver.nonNormalModes(listOf(multiply, screen)),
        )
    }

    private fun media(
        id: String,
        durationUs: Long,
        blendMode: BlendMode,
    ) = Media3BlueprintItem.Media(
        clipId = id,
        assetId = "asset-$id",
        assetType = AssetType.VIDEO,
        uri = "file:///$id.mp4",
        outputTracks = Media3OutputTracks.VIDEO_ONLY,
        sourceMediaDurationUs = durationUs,
        presentationDurationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
        transform = TransformValue(),
        opacity = 1.0,
        volume = 1.0,
        keyframes = emptyList(),
        imageFrameRate = null,
        compositing = CompositingSettings(blendMode = blendMode),
    )
}
