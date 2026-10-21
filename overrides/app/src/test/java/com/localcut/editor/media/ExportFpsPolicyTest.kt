package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFpsPolicyTest {
    @Test
    fun fpsAboveProjectTimelineIsRejectedBecauseFrameInterpolationIsNotEnabled() {
        val project = videoProject(projectFps = 30)
        val profile = ExportProfile(1080, 1920, 60, 8_000_000)

        val result = ExportRequestValidator.validate(project, profile)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ExportProfileException
        assertEquals(ExportProfileErrorCode.INVALID_FPS, error.code)
    }

    @Test
    fun lowerFpsIsAllowedForFrameDropping() {
        val project = videoProject(projectFps = 60)
        val profile = ExportProfile(1080, 1920, 30, 8_000_000)

        assertTrue(ExportRequestValidator.validate(project, profile).isSuccess)
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
