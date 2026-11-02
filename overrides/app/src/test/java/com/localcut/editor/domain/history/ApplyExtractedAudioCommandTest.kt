package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyExtractedAudioCommandTest {
    @Test
    fun detachesAudioIntoOverlayTrackAndUndoRedoIsLossless() {
        val video = asset("video", AssetType.VIDEO, "video/mp4", 10_000_000L)
        val audio = asset("audio", AssetType.AUDIO, "audio/mp4", 10_000_000L)
        val source = Clip(
            id = "clip-video",
            assetId = video.id,
            startUs = 3_000_000L,
            durationUs = 5_000_000L,
            sourceInUs = 2_000_000L,
            sourceOutUs = 7_000_000L,
            volume = 0.8,
            fadeInUs = 200_000L,
            fadeOutUs = 300_000L,
        )
        val project = Project.create("Extract audio", 1080, 1920, 30).copy(
            assets = listOf(video),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(source)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
        val command = ApplyExtractedAudioCommand(
            sourceClipId = source.id,
            extractedAsset = audio,
            extractedClipId = "clip-audio",
            extractedTrackId = "audio-extracted",
        )

        val applied = command.apply(project)
        val mutedSource = requireNotNull(applied.project.findClip(source.id))
        val extracted = requireNotNull(applied.project.findClip("clip-audio"))
        val track = applied.project.tracks.single { it.id == "audio-extracted" }

        assertEquals(0.0, mutedSource.volume, 0.0)
        assertEquals(audio.id, extracted.assetId)
        assertEquals(source.startUs, extracted.startUs)
        assertEquals(source.durationUs, extracted.durationUs)
        assertEquals(source.sourceInUs, extracted.sourceInUs)
        assertEquals(source.sourceOutUs, extracted.sourceOutUs)
        assertEquals(source.volume, extracted.volume, 0.0)
        assertEquals(source.fadeInUs, extracted.fadeInUs)
        assertEquals(source.fadeOutUs, extracted.fadeOutUs)
        assertEquals(source.timeMap, extracted.timeMap)
        assertEquals(source.audioProcessing, extracted.audioProcessing)
        assertEquals(TrackType.AUDIO, track.type)
        assertEquals(TrackCollisionPolicy.OVERLAY, track.collisionPolicy)
        assertTrue(applied.project.assets.any { it.id == audio.id })

        val undone = applied.inverse.apply(applied.project)
        assertEquals(project, undone.project)
        val redone = undone.inverse.apply(undone.project)
        assertEquals(applied.project, redone.project)
    }

    @Test
    fun rejectsWrongAssetTypeDuplicateIdsAndLockedSourceTrack() {
        val video = asset("video", AssetType.VIDEO, "video/mp4", 10_000_000L)
        val source = Clip("clip-video", video.id, 0L, 5_000_000L, 0L, 5_000_000L)
        val locked = Project.create("Extract audio", 1080, 1920, 30).copy(
            assets = listOf(video),
            tracks = listOf(Track("visual-1", TrackType.VISUAL, 0, locked = true, clips = listOf(source))),
        )
        val audio = asset("audio", AssetType.AUDIO, "audio/mp4", 10_000_000L)

        assertTrue(runCatching {
            ApplyExtractedAudioCommand(source.id, audio, "clip-audio", "audio-extracted").apply(locked)
        }.isFailure)
        assertTrue(runCatching {
            ApplyExtractedAudioCommand(
                source.id,
                asset("not-audio", AssetType.VIDEO, "video/mp4", 10_000_000L),
                "clip-audio",
                "audio-extracted",
            ).apply(locked.copy(tracks = locked.tracks.map { it.copy(locked = false) }))
        }.isFailure)
    }

    private fun asset(id: String, type: AssetType, mime: String, durationUs: Long): Asset = Asset(
        id = id,
        type = type,
        uri = "content://$id",
        sourceDurationUs = durationUs,
        sizeBytes = 10L,
        displayName = if (type == AssetType.AUDIO) "$id.m4a" else "$id.mp4",
        mimeType = mime,
        fingerprint = id,
    )
}
