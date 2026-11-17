package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.ProjectSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDrivenTalkPlannerTest {
    @Test
    fun trimmedAudioMapsTimelineIntersectionToSourceAndBodyLocalTime() {
        val project = project(
            image = clip("body", "image", 2_000_000L, 4_000_000L),
            audio = clip("voice", "audio", 3_000_000L, 2_000_000L, sourceInUs = 500_000L),
        )
        val slice = AudioDrivenTalkPlanner.prepare(project, "body", "voice")

        assertEquals(1_000_000L, slice.visualStartOffsetUs)
        assertEquals(2_000_000L, slice.durationUs)
        assertEquals(500_000L, slice.audioSourceInUs)
        assertEquals(2_500_000L, slice.audioSourceOutUs)
        assertEquals("voice", slice.audioClipId)
        assertEquals("voice-fp", slice.audioFingerprint)
        assertTrue(slice.bucketCount in 8..512)
    }

    @Test
    fun partiallyOverlappingTrimmedAudioKeepsCorrectSourceOffset() {
        val project = project(
            image = clip("body", "image", 2_000_000L, 2_000_000L),
            audio = clip("voice", "audio", 1_000_000L, 4_000_000L, sourceInUs = 750_000L),
        )
        val slice = AudioDrivenTalkPlanner.prepare(project, "body", "voice")
        assertEquals(0L, slice.visualStartOffsetUs)
        assertEquals(2_000_000L, slice.durationUs)
        assertEquals(1_750_000L, slice.audioSourceInUs)
        assertEquals(3_750_000L, slice.audioSourceOutUs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonOverlappingAudioIsRejected() {
        AudioDrivenTalkPlanner.prepare(project(
            image = clip("body", "image", 0L, 1_000_000L),
            audio = clip("voice", "audio", 2_000_000L, 1_000_000L),
        ), "body", "voice")
    }

    @Test(expected = IllegalArgumentException::class)
    fun reverseAudioCannotSilentlyProduceIncorrectAlignment() {
        val audio = clip("voice", "audio", 0L, 2_000_000L)
            .copy(timeMap = TimeMap(reverse = true))
        AudioDrivenTalkPlanner.prepare(project(audio = audio), "body", "voice")
    }

    @Test(expected = IllegalArgumentException::class)
    fun mutedAudioTrackIsRejected() {
        AudioDrivenTalkPlanner.prepare(project(audioTrackMuted = true), "body", "voice")
    }

    @Test(expected = IllegalArgumentException::class)
    fun lockedVisualTrackIsRejected() {
        AudioDrivenTalkPlanner.prepare(project(visualTrackLocked = true), "body", "voice")
    }

    @Test(expected = IllegalArgumentException::class)
    fun silentAudioDoesNotTriggerRhythmicFallback() {
        val slice = AudioDrivenTalkPlanner.prepare(project(), "body", "voice")
        AudioDrivenTalkPlanner.plan(project(), slice, MouthRegion(0.5, 0.65, 0.12, 0.08), emptyList())
    }

    @Test
    fun actualVoiceCuesBecomeLocalizedMouthEffectAtTimelineOffset() {
        val project = project(
            image = clip("body", "image", 2_000_000L, 4_000_000L),
            audio = clip("voice", "audio", 3_000_000L, 2_000_000L),
        )
        val slice = AudioDrivenTalkPlanner.prepare(project, "body", "voice")
        val plan = AudioDrivenTalkPlanner.plan(
            project,
            slice,
            MouthRegion(0.5, 0.68, 0.14, 0.08),
            listOf(LipSyncCue(offsetUs = 250_000L, durationUs = 400_000L, openness = 0.8)),
        )

        val effect = plan.effectPatches.single().effects.single()
        assertEquals("body", plan.effectPatches.single().clipId)
        assertEquals("localcut.character.mouth_warp", effect.typeId)
        assertTrue(effect.animations.single().keyframes.any { it.offsetUs == 1_250_000L })
    }

    @Test(expected = IllegalArgumentException::class)
    fun changedRevisionIsRejectedBeforeApply() {
        val project = project()
        val original = AudioDrivenTalkPlanner.prepare(project, "body", "voice")
        AudioDrivenTalkPlanner.requireUnchanged(
            current = ProjectSessionState(project = project, revision = 8L, selectedClipId = "body"),
            expectedRevision = 7L,
            expectedProjectId = project.id,
            expectedSlice = original,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun changedSelectionIsRejectedBeforeApply() {
        val project = project()
        val original = AudioDrivenTalkPlanner.prepare(project, "body", "voice")
        AudioDrivenTalkPlanner.requireUnchanged(
            current = ProjectSessionState(project = project, revision = 7L, selectedClipId = "voice"),
            expectedRevision = 7L,
            expectedProjectId = project.id,
            expectedSlice = original,
        )
    }

    private fun clip(id: String, assetId: String, startUs: Long, durationUs: Long, sourceInUs: Long = 0L): Clip =
        Clip(
            id = id,
            assetId = assetId,
            startUs = startUs,
            durationUs = durationUs,
            sourceInUs = sourceInUs,
            sourceOutUs = sourceInUs + durationUs,
        )

    private fun project(
        image: Clip = clip("body", "image", 0L, 2_000_000L),
        audio: Clip = clip("voice", "audio", 0L, 2_000_000L),
        audioTrackMuted: Boolean = false,
        visualTrackLocked: Boolean = false,
    ): Project {
        val seed = Project.create("Audio lip-sync", 1080, 1920, 30)
        val imageAsset = Asset(
            id = "image", type = AssetType.IMAGE, uri = "content://test/face",
            sourceDurationUs = null, sizeBytes = 20L, displayName = "face.png",
            mimeType = "image/png", fingerprint = "image-fp",
        )
        val audioAsset = Asset(
            id = "audio", type = AssetType.AUDIO, uri = "content://test/voice",
            sourceDurationUs = 10_000_000L, sizeBytes = 200L, displayName = "voice.wav",
            mimeType = "audio/wav", fingerprint = "voice-fp",
        )
        return seed.copy(
            assets = listOf(imageAsset, audioAsset),
            tracks = seed.tracks.map { track: Track ->
                when (track.type) {
                    TrackType.VISUAL -> track.copy(clips = listOf(image), locked = visualTrackLocked)
                    TrackType.AUDIO -> track.copy(clips = listOf(audio), muted = audioTrackMuted)
                }
            },
        )
    }
}
