package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioDuckingSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonDuckingPersistenceTest {
    @Test
    fun duckingSettingsSurviveProjectJsonRoundTrip() {
        val project = projectWithDucking()

        val decoded = ProjectJson.decode(ProjectJson.encode(project))

        assertEquals(
            AudioDuckingSettings(
                sourceTrackId = "voice-track",
                reductionDb = -15.0,
                attackUs = 80_000L,
                releaseUs = 220_000L,
            ),
            decoded.tracks.first { it.id == "music-track" }.ducking,
        )
    }

    @Test
    fun legacyTrackWithoutDuckingDefaultsToNull() {
        val encoded = ProjectJson.encode(projectWithDucking())
        val duckingJson = "\"ducking\":{\"sourceTrackId\":\"voice-track\",\"reductionDb\":-15.0,\"attackUs\":80000,\"releaseUs\":220000},"
        val legacy = encoded.replace(duckingJson, "")

        val decoded = ProjectJson.decode(legacy)

        assertNull(decoded.tracks.first { it.id == "music-track" }.ducking)
    }

    @Test
    fun missingDuckingSourceTrackIsRejected() {
        val project = projectWithDucking().copy(
            tracks = projectWithDucking().tracks.filterNot { it.id == "voice-track" },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ProjectJson.decode(ProjectJson.encode(project))
        }

        assertTrue(error.message.orEmpty().contains("ducking", ignoreCase = true))
    }

    private fun projectWithDucking(): Project {
        val music = asset("music")
        val voice = asset("voice")
        return Project(
            id = "ducking-persistence",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Ducking persistence",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(music, voice),
            tracks = listOf(
                Track(
                    id = "music-track",
                    type = TrackType.AUDIO,
                    order = 0,
                    clips = listOf(clip("music", music.id, 0L, 2_000_000L)),
                    ducking = AudioDuckingSettings(
                        sourceTrackId = "voice-track",
                        reductionDb = -15.0,
                        attackUs = 80_000L,
                        releaseUs = 220_000L,
                    ),
                ),
                Track(
                    id = "voice-track",
                    type = TrackType.AUDIO,
                    order = 1,
                    clips = listOf(clip("voice", voice.id, 500_000L, 500_000L)),
                ),
            ),
        )
    }

    private fun asset(id: String): Asset = Asset(
        id = "$id-asset",
        type = AssetType.AUDIO,
        uri = "content://localcut/$id",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 1L,
        displayName = "$id.m4a",
        mimeType = "audio/mp4",
        fingerprint = "$id-fingerprint",
    )

    private fun clip(id: String, assetId: String, startUs: Long, durationUs: Long): Clip = Clip(
        id = "$id-clip",
        assetId = assetId,
        startUs = startUs,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
    )
}
