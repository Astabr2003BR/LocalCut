package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPreparationTest {
    @Test
    fun invalidProfileIsRejectedBeforeMedia3SettingsAreProduced() {
        val project = videoProject(projectFps = 30)
        val invalid = ExportProfile(
            width = 1080,
            height = 1920,
            fps = 60,
            videoBitrateBitsPerSecond = 8_000_000,
        )

        val result = ExportPreparation.prepare(
            project,
            invalid,
            ExportCapabilities(hevcEncoderAvailable = true),
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ExportProfileException
        assertEquals(ExportProfileErrorCode.INVALID_FPS, error.code)
    }

    @Test
    fun unsupportedHevcIsResolvedBeforeExportSideEffectsBegin() {
        val project = videoProject(projectFps = 30)
        val requested = ExportProfile(
            width = 1080,
            height = 1920,
            fps = 30,
            videoBitrateBitsPerSecond = 8_000_000,
            codec = ExportVideoCodec.HEVC,
        )

        val prepared = ExportPreparation.prepare(
            project,
            requested,
            ExportCapabilities(hevcEncoderAvailable = false),
        ).getOrThrow()

        assertEquals(ExportVideoCodec.H264, prepared.effectiveProfile.codec)
        assertTrue(prepared.usedCodecFallback)
        assertEquals(RenderOutputSettings.forExport(prepared.effectiveProfile), prepared.outputSettings)
    }

    private fun videoProject(projectFps: Int): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 5_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        val clip = Clip(
            id = "clip",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
        )
        val base = Project.create("Video", 1080, 1920, projectFps)
        return base.copy(
            assets = listOf(asset),
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(clips = listOf(clip)) else track
            },
        )
    }
}
