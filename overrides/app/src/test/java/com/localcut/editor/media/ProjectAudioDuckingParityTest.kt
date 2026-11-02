package com.localcut.editor.media

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioDuckingSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAudioDuckingParityTest {
    private val resolver = RenderAssetResolver { asset -> Result.success("resolved://${asset.id}") }

    @Test
    fun previewAndExportCompileTheSameDuckingEnvelope() {
        val project = project(sourceMuted = false, targetSolo = false)

        val preview = targetMedia(project, RenderMode.FAST_PREVIEW)
        val export = targetMedia(project, RenderMode.EXPORT)

        assertTrue(preview.duckingEnvelope.windows.isNotEmpty())
        assertEquals(preview.duckingEnvelope, export.duckingEnvelope)
        assertEquals(3_000_000L, preview.durationUs)
        assertEquals(3_000_000L, export.durationUs)
    }

    @Test
    fun mutedSidechainSourceDoesNotDuckTarget() {
        val project = project(sourceMuted = true, targetSolo = false)

        val target = targetMedia(project, RenderMode.EXPORT)

        assertTrue(target.duckingEnvelope.windows.isEmpty())
    }

    @Test
    fun sidechainExcludedBySoloPolicyDoesNotDuckTarget() {
        val project = project(sourceMuted = false, targetSolo = true)

        val target = targetMedia(project, RenderMode.EXPORT)

        assertTrue(target.duckingEnvelope.windows.isEmpty())
    }

    private fun targetMedia(project: Project, mode: RenderMode): RenderSegment.Media =
        ProjectRenderPlanCompiler.compile(project, mode, resolver)
            .getOrThrow()
            .audioLayers
            .first { it.sourceTrackId == "music-track" }
            .segments
            .filterIsInstance<RenderSegment.Media>()
            .single()

    private fun project(sourceMuted: Boolean, targetSolo: Boolean): Project {
        val video = asset("video", AssetType.VIDEO, "video/mp4")
        val music = asset("music", AssetType.AUDIO, "audio/mp4")
        val voice = asset("voice", AssetType.AUDIO, "audio/mp4")
        return Project(
            id = "ducking-parity",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Ducking parity",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(video, music, voice),
            tracks = listOf(
                Track(
                    id = "visual-track",
                    type = TrackType.VISUAL,
                    order = 0,
                    clips = listOf(clip("video", video.id, 0L, 3_000_000L)),
                ),
                Track(
                    id = "music-track",
                    type = TrackType.AUDIO,
                    order = 1,
                    clips = listOf(clip("music", music.id, 0L, 3_000_000L)),
                    solo = targetSolo,
                    ducking = AudioDuckingSettings(
                        sourceTrackId = "voice-track",
                        reductionDb = -12.0,
                        attackUs = 100_000L,
                        releaseUs = 250_000L,
                    ),
                ),
                Track(
                    id = "voice-track",
                    type = TrackType.AUDIO,
                    order = 2,
                    muted = sourceMuted,
                    clips = listOf(clip("voice", voice.id, 1_000_000L, 750_000L)),
                ),
            ),
        )
    }

    private fun asset(id: String, type: AssetType, mime: String): Asset = Asset(
        id = "$id-asset",
        type = type,
        uri = "content://localcut/$id",
        sourceDurationUs = 3_000_000L,
        sizeBytes = 1L,
        displayName = "$id.bin",
        mimeType = mime,
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
