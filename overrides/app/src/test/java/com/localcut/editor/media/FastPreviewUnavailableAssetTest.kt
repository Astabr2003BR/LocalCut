package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastPreviewUnavailableAssetTest {
    @Test
    fun fastPreviewKeepsRemainingTimelinePlayableWhenLegacyAssetIsUnavailable() {
        val unavailableImage = Asset(
            id = "old-image",
            type = AssetType.IMAGE,
            uri = "content://legacy-share",
            sourceDurationUs = null,
            sizeBytes = 100,
            displayName = "218803.jpg",
            mimeType = "image/jpeg",
            fingerprint = "legacy",
        )
        val validVideo = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://valid-video",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 200,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        val base = Project.create("preview", 1080, 1920, 30)
        val project = base.copy(
            assets = listOf(unavailableImage, validVideo),
            tracks = base.tracks.map { track ->
                when (track.type) {
                    TrackType.VISUAL -> track.copy(
                        clips = listOf(
                            Clip("old", "old-image", 0L, 3_000_000L, 0L, 3_000_000L),
                            Clip("new", "video", 3_000_000L, 4_000_000L, 0L, 4_000_000L),
                        ),
                    )
                    TrackType.AUDIO -> track
                }
            },
        )
        val resolver = RenderAssetResolver { asset ->
            if (asset.id == "old-image") {
                Result.failure(IllegalStateException("revoked legacy share permission"))
            } else {
                Result.success("resolved://${asset.id}")
            }
        }

        val preview = ProjectRenderPlanCompiler.compile(
            project = project,
            mode = RenderMode.FAST_PREVIEW,
            resolver = resolver,
        ).getOrThrow()

        assertEquals(7_000_000L, preview.durationUs)
        assertTrue(preview.visual.segments[0] is RenderSegment.Gap)
        assertEquals(3_000_000L, preview.visual.segments[0].durationUs)
        val playable = preview.visual.segments[1] as RenderSegment.Media
        assertEquals("new", playable.clipId)
        assertEquals("resolved://video", playable.uri)
    }

    @Test
    fun exportStillFailsWhenAnyReferencedAssetIsUnavailable() {
        val image = Asset(
            id = "image",
            type = AssetType.IMAGE,
            uri = "content://missing",
            sourceDurationUs = null,
            sizeBytes = 10,
            displayName = "missing.jpg",
            mimeType = "image/jpeg",
            fingerprint = "missing",
        )
        val base = Project.create("export", 1080, 1920, 30)
        val project = base.copy(
            assets = listOf(image),
            tracks = base.tracks.map { track ->
                if (track.type == TrackType.VISUAL) {
                    track.copy(clips = listOf(Clip("clip", "image", 0L, 1_000_000L, 0L, 1_000_000L)))
                } else {
                    track
                }
            },
        )
        val resolver = RenderAssetResolver {
            Result.failure(IllegalStateException("missing"))
        }

        val result = ProjectRenderPlanCompiler.compile(project, RenderMode.EXPORT, resolver)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as RenderPlanException
        assertEquals(RenderPlanErrorCode.ASSET_UNAVAILABLE, error.code)
    }
}
