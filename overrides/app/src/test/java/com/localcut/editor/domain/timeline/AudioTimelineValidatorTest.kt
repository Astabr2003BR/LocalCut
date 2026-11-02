package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTimelineValidatorTest {
    @Test
    fun invalidClipAudioProcessingIsRejected() {
        val asset = Asset("a", AssetType.AUDIO, "content://a", 1_000_000L, 1L, "a.wav", "audio/wav", "fp")
        val clip = Clip(
            id = "c",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            audioProcessing = AudioProcessingSettings(pan = 2.0),
        )
        val project = Project.create("Audio", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(Track("visual-1", TrackType.VISUAL, 0), Track("audio-1", TrackType.AUDIO, 1, clips = listOf(clip))),
        )

        assertTrue(TimelineValidator.validate(project).any { it.code == ProjectIssueCode.INVALID_AUDIO_PROCESSING && it.subjectId == "c" })
    }

    @Test
    fun invalidBeatMarkersAreRejected() {
        val project = Project.create("Beats", 1080, 1920, 30).copy(
            beatMarkers = listOf(
                BeatMarker("b2", 2_000_000L, 1.0),
                BeatMarker("b1", 1_000_000L, 1.0),
            ),
        )

        assertTrue(TimelineValidator.validate(project).any { it.code == ProjectIssueCode.INVALID_AUDIO_PROCESSING && it.subjectId == project.id })
    }
}
