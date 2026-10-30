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

class Media3SequenceBlendModeResolverTest {
    @Test
    fun activeMediaUsesItsBlendModeWhileGapsAndOutsideRangeAreNormal() {
        val sequence = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(
                Media3BlueprintItem.Gap(100L),
                media(durationUs = 100L, mode = BlendMode.MULTIPLY),
                Media3BlueprintItem.Gap(100L),
            ),
        )

        assertEquals(BlendMode.NORMAL, Media3SequenceBlendModeResolver.blendModeAt(sequence, -1L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendModeResolver.blendModeAt(sequence, 0L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendModeResolver.blendModeAt(sequence, 99L))
        assertEquals(BlendMode.MULTIPLY, Media3SequenceBlendModeResolver.blendModeAt(sequence, 100L))
        assertEquals(BlendMode.MULTIPLY, Media3SequenceBlendModeResolver.blendModeAt(sequence, 199L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendModeResolver.blendModeAt(sequence, 200L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendModeResolver.blendModeAt(sequence, 300L))
    }

    @Test
    fun adjacentMediaSwitchesModeExactlyAtBoundary() {
        val sequence = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(
                media(durationUs = 50L, mode = BlendMode.SCREEN),
                media(durationUs = 50L, mode = BlendMode.OVERLAY),
            ),
        )

        assertEquals(BlendMode.SCREEN, Media3SequenceBlendModeResolver.blendModeAt(sequence, 49L))
        assertEquals(BlendMode.OVERLAY, Media3SequenceBlendModeResolver.blendModeAt(sequence, 50L))
        assertEquals(BlendMode.OVERLAY, Media3SequenceBlendModeResolver.blendModeAt(sequence, 99L))
        assertEquals(BlendMode.NORMAL, Media3SequenceBlendModeResolver.blendModeAt(sequence, 100L))
    }

    @Test
    fun customBlendDetectionIgnoresNormalAndGapsButFindsAnyNonNormalMedia() {
        val normal = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(Media3BlueprintItem.Gap(10L), media(10L, BlendMode.NORMAL)),
        )
        val custom = Media3SequenceBlueprint(
            type = TrackType.VISUAL,
            items = listOf(media(10L, BlendMode.NORMAL), media(10L, BlendMode.LIGHTEN)),
        )

        assertFalse(Media3SequenceBlendModeResolver.requiresCustomBlend(listOf(normal)))
        assertTrue(Media3SequenceBlendModeResolver.requiresCustomBlend(listOf(normal, custom)))
        assertFalse(Media3SequenceBlendModeResolver.requiresCustomBlend(emptyList()))
    }

    private fun media(durationUs: Long, mode: BlendMode): Media3BlueprintItem.Media =
        Media3BlueprintItem.Media(
            clipId = "clip-$mode-$durationUs",
            assetId = "asset-$mode",
            assetType = AssetType.VIDEO,
            uri = "content://$mode",
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
            compositing = CompositingSettings(blendMode = mode),
        )
}
