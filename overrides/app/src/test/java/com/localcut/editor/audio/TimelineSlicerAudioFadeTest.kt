package com.localcut.editor.audio

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineSlicer
import com.localcut.editor.domain.timeline.TimelineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineSlicerAudioFadeTest {
    @Test
    fun internalRangeDropsEdgeAnchoredFadesAndRemainsValid() {
        val sliced = TimelineSlicer.slice(projectWithFades(), 1_500_000L, 2_500_000L)
        val clip = sliced.findClip("audio-clip")!!

        assertEquals(1_000_000L, clip.durationUs)
        assertEquals(0L, clip.fadeInUs)
        assertEquals(0L, clip.fadeOutUs)
        assertTrue(TimelineValidator.validate(sliced).isEmpty())
    }

    @Test
    fun sliceThatKeepsOriginalEdgesPreservesOnlyCompatibleOuterFades() {
        val left = TimelineSlicer.slice(projectWithFades(), 0L, 2_000_000L).findClip("audio-clip")!!
        assertEquals(1_000_000L, left.fadeInUs)
        assertEquals(0L, left.fadeOutUs)

        val right = TimelineSlicer.slice(projectWithFades(), 2_000_000L, 4_000_000L).findClip("audio-clip")!!
        assertEquals(0L, right.fadeInUs)
        assertEquals(1_000_000L, right.fadeOutUs)
    }

    private fun projectWithFades(): Project {
        val asset = Asset(
            id = "audio-asset",
            type = AssetType.AUDIO,
            uri = "content://audio/fades",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 1L,
            displayName = "fades.mp3",
            mimeType = "audio/mpeg",
            fingerprint = "fade-fingerprint",
        )
        val clip = Clip(
            id = "audio-clip",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
            fadeInUs = 1_000_000L,
            fadeOutUs = 1_000_000L,
        )
        val base = Project.create("Fade slice", 1080, 1920, 30)
        return base.copy(
            assets = listOf(asset),
            tracks = base.tracks.map { track ->
                if (track.type == TrackType.AUDIO) track.copy(clips = listOf(clip)) else track
            },
        )
    }
}
