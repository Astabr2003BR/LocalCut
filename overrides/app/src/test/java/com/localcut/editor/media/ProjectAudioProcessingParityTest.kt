package com.localcut.editor.media

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.LimiterSettings
import com.localcut.editor.domain.model.NoiseGateSettings
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectAudioProcessingParityTest {
    private val resolver = RenderAssetResolver { asset -> Result.success("resolved://${asset.id}") }

    @Test
    fun audioProcessingSurvivesPreviewAndExportRenderPlanningWithoutDivergence() {
        val processing = AudioProcessingSettings(
            eqBands = listOf(
                EqBand(frequencyHz = 120.0, gainDb = 2.5, q = 0.8),
                EqBand(frequencyHz = 4_000.0, gainDb = -1.5, q = 1.4),
            ),
            compressor = CompressorSettings(
                thresholdDb = -20.0,
                ratio = 3.0,
                attackMs = 8.0,
                releaseMs = 150.0,
                makeupGainDb = 1.5,
            ),
            limiter = LimiterSettings(ceilingDb = -0.8, releaseMs = 90.0),
            noiseGate = NoiseGateSettings(thresholdDb = -48.0, attackMs = 4.0, releaseMs = 180.0),
            pan = 0.35,
            normalizationGainDb = 2.0,
            pitchSemitones = 7.0,
        )
        val asset = Asset(
            id = "video-1",
            type = AssetType.VIDEO,
            uri = "content://video/1",
            sourceDurationUs = 3_000_000L,
            sizeBytes = 100L,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            fingerprint = "fingerprint-1",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 3_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 3_000_000L,
            audioProcessing = processing,
        )
        val project = Project(
            id = "audio-processing-parity",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Audio processing parity",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    clips = listOf(clip),
                ),
            ),
        )

        val preview = ProjectRenderPlanCompiler
            .compile(project, RenderMode.FAST_PREVIEW, resolver)
            .getOrThrow()
            .visualLayers.single()
            .segments.filterIsInstance<RenderSegment.Media>()
            .single()
        val export = ProjectRenderPlanCompiler
            .compile(project, RenderMode.EXPORT, resolver)
            .getOrThrow()
            .visualLayers.single()
            .segments.filterIsInstance<RenderSegment.Media>()
            .single()

        assertEquals(processing, preview.audioProcessing)
        assertEquals(processing, export.audioProcessing)
        assertEquals(preview.audioProcessing, export.audioProcessing)
    }
}
