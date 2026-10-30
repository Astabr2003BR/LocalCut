package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProjectJsonV6Test {
    @Test
    fun schemaV6RoundTripsAudioProcessingAndBeatMarkers() {
        val asset = Asset(
            id = "audio",
            type = AssetType.AUDIO,
            uri = "content://audio",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 1L,
            displayName = "audio.wav",
            mimeType = "audio/wav",
            fingerprint = "fp",
        )
        val processing = AudioProcessingSettings(
            eqBands = listOf(EqBand(1_000.0, 2.5, 0.8)),
            compressor = CompressorSettings(-18.0, 4.0, 8.0, 120.0, 1.0),
            limiter = LimiterSettings(-1.0, 90.0),
            noiseGate = NoiseGateSettings(-45.0, 4.0, 160.0),
            pan = 0.25,
            normalizationGainDb = 1.5,
        )
        val clip = Clip(
            id = "clip-a",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
            audioProcessing = processing,
        )
        val markers = listOf(
            BeatMarker("beat-1", 500_000L, 0.7),
            BeatMarker("beat-2", 1_000_000L, 1.0),
        )
        val project = Project.create("Audio", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0),
                Track("audio-1", TrackType.AUDIO, 1, clips = listOf(clip)),
            ),
            beatMarkers = markers,
        )

        val encoded = ProjectJson.encode(project)
        val decoded = ProjectJson.decode(encoded)

        assertEquals(7, BuildContract.PROJECT_SCHEMA_VERSION)
        assertTrue(encoded.contains("\"schemaVersion\":7"))
        assertEquals(processing, decoded.findClip("clip-a")!!.audioProcessing)
        assertEquals(markers, decoded.beatMarkers)
    }

    @Test
    fun schemaV5MigratesAudioStateToNeutralDefaults() {
        val v5 = ProjectJson.encode(Project.create("Legacy", 1080, 1920, 30))
            .replace("\"schemaVersion\":7", "\"schemaVersion\":5")
            .replace(",\"designGroups\":[]", "")
            .replace(Regex(",\"beatMarkers\":.*$"), "}")
            .replace(Regex(",\"audioProcessing\":\\{.*?}([,}])"), "$1")

        val decoded = ProjectJson.decode(v5)

        assertEquals(7, decoded.schemaVersion)
        assertTrue(decoded.beatMarkers.isEmpty())
        assertTrue(decoded.designGroups.isEmpty())
    }
}
