package com.localcut.editor.audio

import com.localcut.editor.domain.history.HistoryManager
import com.localcut.editor.domain.history.SetAudioFadesCommand
import com.localcut.editor.domain.history.SetTrackMutedCommand
import com.localcut.editor.domain.history.SetTrackSoloCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineEditException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioControlCommandsTest {
    @Test
    fun fadeCommandParticipatesInUndoRedoWithoutLosingEnvelope() {
        val history = HistoryManager(projectWithAudio())

        history.execute(SetAudioFadesCommand("audio-clip", 500_000L, 750_000L))
        assertEquals(500_000L, history.project.findClip("audio-clip")!!.fadeInUs)
        assertEquals(750_000L, history.project.findClip("audio-clip")!!.fadeOutUs)

        assertTrue(history.undo())
        assertEquals(0L, history.project.findClip("audio-clip")!!.fadeInUs)
        assertEquals(0L, history.project.findClip("audio-clip")!!.fadeOutUs)

        assertTrue(history.redo())
        assertEquals(500_000L, history.project.findClip("audio-clip")!!.fadeInUs)
        assertEquals(750_000L, history.project.findClip("audio-clip")!!.fadeOutUs)
    }

    @Test
    fun muteAndSoloCommandsUndoAndRedoInOrder() {
        val history = HistoryManager(projectWithAudio())

        history.execute(SetTrackMutedCommand("audio-1", true))
        history.execute(SetTrackSoloCommand("audio-1", true))
        assertTrue(audioTrack(history.project).muted)
        assertTrue(audioTrack(history.project).solo)

        assertTrue(history.undo())
        assertTrue(audioTrack(history.project).muted)
        assertFalse(audioTrack(history.project).solo)
        assertTrue(history.undo())
        assertFalse(audioTrack(history.project).muted)
        assertFalse(audioTrack(history.project).solo)

        assertTrue(history.redo())
        assertTrue(audioTrack(history.project).muted)
        assertFalse(audioTrack(history.project).solo)
        assertTrue(history.redo())
        assertTrue(audioTrack(history.project).muted)
        assertTrue(audioTrack(history.project).solo)
    }

    @Test
    fun overlappingFadesAreRejectedBeforeHistoryIsMutated() {
        val initial = projectWithAudio()
        val history = HistoryManager(initial)

        assertThrows(TimelineEditException::class.java) {
            history.execute(SetAudioFadesCommand("audio-clip", 2_500_000L, 2_500_000L))
        }

        assertEquals(initial, history.project)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    private fun audioTrack(project: Project) = project.tracks.first { it.id == "audio-1" }

    private fun projectWithAudio(): Project {
        val asset = Asset(
            id = "audio-asset",
            type = AssetType.AUDIO,
            uri = "content://audio/fixture",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 1L,
            displayName = "fixture.mp3",
            mimeType = "audio/mpeg",
            fingerprint = "fixture-fingerprint",
        )
        val clip = Clip(
            id = "audio-clip",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
        )
        val base = Project.create("Audio commands", 1080, 1920, 30)
        return base.copy(
            assets = listOf(asset),
            tracks = base.tracks.map { track ->
                if (track.type == TrackType.AUDIO) track.copy(clips = listOf(clip)) else track
            },
        )
    }
}
