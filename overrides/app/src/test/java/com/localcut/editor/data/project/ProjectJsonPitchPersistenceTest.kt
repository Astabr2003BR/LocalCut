package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonPitchPersistenceTest {
    @Test
    fun pitchSemitonesSurvivesProjectJsonRoundTrip() {
        val project = projectWithPitch(7.0)

        val decoded = ProjectJson.decode(ProjectJson.encode(project))

        assertEquals(7.0, decoded.findClip("clip-1")!!.audioProcessing.pitchSemitones, 0.0)
    }

    @Test
    fun legacyAudioProcessingWithoutPitchDefaultsToZero() {
        val encoded = ProjectJson.encode(projectWithPitch(0.0))
        val legacy = encoded.replace(",\"pitchSemitones\":0.0", "")

        val decoded = ProjectJson.decode(legacy)

        assertEquals(0.0, decoded.findClip("clip-1")!!.audioProcessing.pitchSemitones, 0.0)
    }

    @Test
    fun invalidPersistedPitchIsRejectedByProjectValidation() {
        val encoded = ProjectJson.encode(projectWithPitch(7.0))
        val tampered = encoded.replace("\"pitchSemitones\":7.0", "\"pitchSemitones\":99.0")

        val error = assertThrows(IllegalArgumentException::class.java) {
            ProjectJson.decode(tampered)
        }

        assertTrue(error.message.orEmpty().contains("pitch semitones", ignoreCase = true))
    }

    private fun projectWithPitch(pitchSemitones: Double): Project {
        val asset = Asset(
            id = "audio-1",
            type = AssetType.AUDIO,
            uri = "content://localcut/test-audio",
            sourceDurationUs = 1_000_000L,
            sizeBytes = 1L,
            displayName = "test.m4a",
            mimeType = "audio/mp4",
            fingerprint = "test-audio-fingerprint",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            audioProcessing = AudioProcessingSettings(pitchSemitones = pitchSemitones),
        )
        return Project(
            id = "pitch-persistence",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Pitch persistence",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "audio-track-1",
                    type = TrackType.AUDIO,
                    order = 0,
                    clips = listOf(clip),
                ),
            ),
        )
    }
}
