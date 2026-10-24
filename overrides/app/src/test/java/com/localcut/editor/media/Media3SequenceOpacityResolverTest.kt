package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3SequenceOpacityResolverTest {
    private fun media(
        clipId: String,
        durationUs: Long = 1_000_000L,
        opacity: Double = 1.0,
        transform: TransformValue = TransformValue(),
        keyframes: List<TransformKeyframe> = emptyList(),
    ) = Media3BlueprintItem.Media(
        clipId = clipId,
        assetId = "asset-$clipId",
        assetType = AssetType.IMAGE,
        uri = "file:///$clipId.png",
        outputTracks = Media3OutputTracks.VIDEO_ONLY,
        sourceMediaDurationUs = durationUs,
        presentationDurationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
        transform = transform,
        opacity = opacity,
        volume = 1.0,
        keyframes = keyframes,
        imageFrameRate = 30,
    )

    @Test
    fun secondItemOpacityUsesItsOwnLocalKeyframeTime() {
        val sequence = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(
                media("first"),
                media(
                    "second",
                    opacity = 0.8,
                    keyframes = listOf(
                        TransformKeyframe(0L, TransformValue(opacity = 1.0)),
                        TransformKeyframe(500_000L, TransformValue(opacity = 0.5)),
                    ),
                ),
            ),
        )

        assertEquals(
            0.6f,
            Media3SequenceOpacityResolver.alphaAt(sequence, 1_250_000L),
            0.0001f,
        )
    }

    @Test
    fun baseLayerGapsAndTimesOutsideMediaKeepNeutralAlpha() {
        val sequence = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(
                Media3BlueprintItem.Gap(500_000L),
                media("clip", opacity = 0.5),
            ),
            sourceCollisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
        )
        assertEquals(1.0f, Media3SequenceOpacityResolver.alphaAt(sequence, 250_000L), 0.0001f)
        assertEquals(0.5f, Media3SequenceOpacityResolver.alphaAt(sequence, 750_000L), 0.0001f)
        assertEquals(1.0f, Media3SequenceOpacityResolver.alphaAt(sequence, 2_000_000L), 0.0001f)
    }

    @Test
    fun overlayLayerGapsAndTimesOutsideMediaAreTransparent() {
        val sequence = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(
                Media3BlueprintItem.Gap(500_000L),
                media("overlay"),
                Media3BlueprintItem.Gap(500_000L),
            ),
            sourceCollisionPolicy = TrackCollisionPolicy.OVERLAY,
        )

        assertEquals(0f, Media3SequenceOpacityResolver.alphaAt(sequence, 250_000L), 0.0001f)
        assertEquals(1f, Media3SequenceOpacityResolver.alphaAt(sequence, 750_000L), 0.0001f)
        assertEquals(0f, Media3SequenceOpacityResolver.alphaAt(sequence, 1_750_000L), 0.0001f)
        assertEquals(0f, Media3SequenceOpacityResolver.alphaAt(sequence, 3_000_000L), 0.0001f)
    }

    @Test
    fun overlaySequenceRequiresCustomCompositorEvenAtFullOpacity() {
        val overlay = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(Media3BlueprintItem.Gap(500_000L), media("overlay")),
            sourceCollisionPolicy = TrackCollisionPolicy.OVERLAY,
        )

        assertTrue(Media3SequenceOpacityResolver.requiresCustomAlpha(listOf(overlay)))
    }

    @Test
    fun customCompositorIsRequiredWhenOpacityCanChangePixels() {
        val neutral = Media3SequenceBlueprint(TrackType.VISUAL, listOf(media("neutral")))
        val staticAlpha = Media3SequenceBlueprint(
            TrackType.VISUAL,
            listOf(media("static", transform = TransformValue(opacity = 0.75))),
        )
        val animatedAlpha = Media3SequenceBlueprint(
            TrackType.VISUAL,
            listOf(
                media(
                    "animated",
                    keyframes = listOf(TransformKeyframe(0L, TransformValue(opacity = 0.9))),
                ),
            ),
        )

        assertFalse(Media3SequenceOpacityResolver.requiresCustomAlpha(listOf(neutral)))
        assertTrue(Media3SequenceOpacityResolver.requiresCustomAlpha(listOf(staticAlpha)))
        assertTrue(Media3SequenceOpacityResolver.requiresCustomAlpha(listOf(animatedAlpha)))
    }
}
