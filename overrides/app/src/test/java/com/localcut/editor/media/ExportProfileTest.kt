package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportProfileTest {
    @Test
    fun validH264ProfileIsPreserved() {
        val requested = ExportProfile(
            width = 1920,
            height = 1080,
            fps = 30,
            videoBitrateBitsPerSecond = 8_000_000,
            codec = ExportVideoCodec.H264,
        )

        val resolved = ExportProfileResolver.resolve(
            requested,
            ExportCapabilities(hevcEncoderAvailable = false),
        ).getOrThrow()

        assertEquals(requested, resolved.effective)
        assertFalse(resolved.usedCodecFallback)
    }

    @Test
    fun unsupportedHevcFallsBackToH264WithoutChangingOtherSettings() {
        val requested = ExportProfile(
            width = 1280,
            height = 720,
            fps = 30,
            videoBitrateBitsPerSecond = 4_000_000,
            codec = ExportVideoCodec.HEVC,
        )

        val resolved = ExportProfileResolver.resolve(
            requested,
            ExportCapabilities(hevcEncoderAvailable = false),
        ).getOrThrow()

        assertEquals(ExportVideoCodec.H264, resolved.effective.codec)
        assertEquals(requested.copy(codec = ExportVideoCodec.H264), resolved.effective)
        assertTrue(resolved.usedCodecFallback)
    }

    @Test
    fun invalidResolutionFpsAndBitrateAreRejected() {
        val invalidProfiles = listOf(
            ExportProfile(0, 1080, 30, 8_000_000),
            ExportProfile(1919, 1080, 30, 8_000_000),
            ExportProfile(1920, 1080, 0, 8_000_000),
            ExportProfile(1920, 1080, 121, 8_000_000),
            ExportProfile(1920, 1080, 30, 99_999),
        )

        invalidProfiles.forEach { profile ->
            assertTrue(
                "expected rejection for $profile",
                ExportProfileResolver.resolve(profile, ExportCapabilities()).isFailure,
            )
        }
    }

    @Test
    fun emptyProjectIsRejectedBeforeExportStarts() {
        val project = Project.create("Empty", 1080, 1920, 30)
        val profile = ExportProfile(1080, 1920, 30, 8_000_000)

        val result = ExportRequestValidator.validate(project, profile)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ExportProfileException
        assertEquals(ExportProfileErrorCode.EMPTY_PROJECT, error.code)
    }

    @Test
    fun projectWithVisualMediaAndPositiveDurationIsAccepted() {
        val asset = Asset(
            id = "video-1",
            type = AssetType.VIDEO,
            uri = "content://video/1",
            sourceDurationUs = 5_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video-1",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
        )
        val base = Project.create("Video", 1080, 1920, 30)
        val project = base.copy(
            assets = listOf(asset),
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(clips = listOf(clip)) else track
            },
        )
        val profile = ExportProfile(1080, 1920, 30, 8_000_000)

        assertTrue(ExportRequestValidator.validate(project, profile).isSuccess)
    }
}
