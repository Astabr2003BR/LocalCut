package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectModelV2RenderCompatibilityTest {
    @Test
    fun v0RendererRejectsOverlayTrackUntilCompositorSupportsIt() {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 10_000_000,
            sizeBytes = 1,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        val base = Project.create("overlay", 1080, 1920, 30).copy(assets = listOf(asset))
        val project = base.copy(
            tracks = base.tracks.map { track ->
                if (track.type == TrackType.VISUAL) {
                    track.copy(
                        collisionPolicy = TrackCollisionPolicy.OVERLAY,
                        clips = listOf(
                            Clip("a", asset.id, 0, 2_000_000, 0, 2_000_000),
                            Clip("b", asset.id, 1_000_000, 2_000_000, 2_000_000, 4_000_000),
                        ),
                    )
                } else {
                    track
                }
            },
        )
        val resolver = RenderAssetResolver { Result.success(it.uri) }

        val result = ProjectRenderPlanCompiler.compile(project, RenderMode.EXPORT, resolver)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as RenderPlanException
        assertEquals(RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT, error.code)
    }
}
