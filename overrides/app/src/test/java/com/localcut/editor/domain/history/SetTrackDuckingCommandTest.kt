package com.localcut.editor.domain.history

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
import org.junit.Test

class SetTrackDuckingCommandTest {
    @Test
    fun applyAndInverseRestoreTrackDuckingExactly() {
        val project = project()
        val settings = AudioDuckingSettings(
            sourceTrackId = "voice-track",
            reductionDb = -18.0,
            attackUs = 80_000L,
            releaseUs = 240_000L,
        )

        val applied = SetTrackDuckingCommand("music-track", settings).apply(project)

        assertEquals(settings, applied.project.tracks.first { it.id == "music-track" }.ducking)
        val restored = applied.inverse.apply(applied.project)
        assertNull(restored.project.tracks.first { it.id == "music-track" }.ducking)
    }

    private fun project(): Project {
        val music = asset("music")
        val voice = asset("voice")
        return Project(
            id = "ducking-history",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Ducking history",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(music, voice),
            tracks = listOf(
                Track(
                    id = "music-track",
                    type = TrackType.AUDIO,
                    order = 0,
                    clips = listOf(clip("music", music.id)),
                ),
                Track(
                    id = "voice-track",
                    type = TrackType.AUDIO,
                    order = 1,
                    clips = listOf(clip("voice", voice.id)),
                ),
            ),
        )
    }

    private fun asset(id: String): Asset = Asset(
        id = "$id-asset",
        type = AssetType.AUDIO,
        uri = "content://localcut/$id",
        sourceDurationUs = 1_000_000L,
        sizeBytes = 1L,
        displayName = "$id.m4a",
        mimeType = "audio/mp4",
        fingerprint = "$id-fingerprint",
    )

    private fun clip(id: String, assetId: String): Clip = Clip(
        id = "$id-clip",
        assetId = assetId,
        startUs = 0L,
        durationUs = 1_000_000L,
        sourceInUs = 0L,
        sourceOutUs = 1_000_000L,
    )
}
