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

class KeyframeVideoCompositorSettingsBlendTest {
    @Test
    fun settingsExposeTimeResolvedBlendModeForEachInput() {
        val first = sequence(
            listOf(
                Media3BlueprintItem.Gap(50L),
                media(50L, BlendMode.MULTIPLY),
            ),
        )
        val second = sequence(listOf(media(100L, BlendMode.SCREEN)))
        val settings = KeyframeVideoCompositorSettings(listOf(first, second))

        assertEquals(BlendMode.NORMAL, settings.blendModeAt(0, 49L))
        assertEquals(BlendMode.MULTIPLY, settings.blendModeAt(0, 50L))
        assertEquals(BlendMode.SCREEN, settings.blendModeAt(1, 50L))
        assertEquals(BlendMode.NORMAL, settings.blendModeAt(99, 50L))
        assertTrue(settings.requiresCustomBlend())
    }

    @Test
    fun normalOnlySettingsDoNotRequireCustomBlend() {
        val settings = KeyframeVideoCompositorSettings(
            listOf(sequence(listOf(media(100L, BlendMode.NORMAL)))),
        )

        assertFalse(settings.requiresCustomBlend())
    }

    private fun sequence(items: List<Media3BlueprintItem>): Media3SequenceBlueprint =
        Media3SequenceBlueprint(type = TrackType.VISUAL, items = items)

    private fun media(durationUs: Long, mode: BlendMode): Media3BlueprintItem.Media =
        Media3BlueprintItem.Media(
            clipId = "clip-$mode",
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
