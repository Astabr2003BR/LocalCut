package com.localcut.editor.media

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRenderPlanMultitrackTest {
    private val resolver = RenderAssetResolver { asset -> Result.success("resolved://${asset.id}") }

    @Test
    fun twoVisualTracksCompileIntoOrderedVisualLayersWhileLegacyVisualPointsAtBaseLayer() {
        val result = ProjectRenderPlanCompiler.compile(
            twoVisualTrackProject(),
            RenderMode.EXPORT,
            resolver,
        )

        val plan = result.getOrThrow()
        assertEquals(2, plan.visualLayers.size)

        val baseMedia = plan.visualLayers[0].segments.filterIsInstance<RenderSegment.Media>().single()
        val overlayMedia = plan.visualLayers[1].segments.filterIsInstance<RenderSegment.Media>().single()
        assertEquals("base-clip", baseMedia.clipId)
        assertEquals("overlay-clip", overlayMedia.clipId)
        assertEquals(plan.visualLayers.first(), plan.visual)

        assertEquals(
            listOf(1_000_000L, 2_000_000L, 1_000_000L),
            plan.visualLayers[1].segments.map { it.durationUs },
        )
        assertTrue(plan.visualLayers[1].segments.first() is RenderSegment.Gap)
        assertTrue(plan.visualLayers[1].segments.last() is RenderSegment.Gap)
    }

    @Test
    fun multipleAudioTracksRemainRejectedUntilAudioMixingStage() {
        val base = twoVisualTrackProject()
        val extraAudio = Track(
            id = "audio-2",
            type = TrackType.AUDIO,
            order = 3,
            collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
        )
        val project = base.copy(tracks = base.tracks + extraAudio)

        val result = ProjectRenderPlanCompiler.compile(project, RenderMode.EXPORT, resolver)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as RenderPlanException
        assertEquals(RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT, error.code)
    }

    private fun twoVisualTrackProject(): Project {
        val baseAsset = Asset(
            id = "base",
            type = AssetType.VIDEO,
            uri = "content://video/base",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 100L,
            displayName = "base.mp4",
            mimeType = "video/mp4",
            fingerprint = "base-fingerprint",
        )
        val overlayAsset = Asset(
            id = "overlay",
            type = AssetType.VIDEO,
            uri = "content://video/overlay",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 50L,
            displayName = "overlay.mp4",
            mimeType = "video/mp4",
            fingerprint = "overlay-fingerprint",
        )
        return Project(
            id = "multitrack-render",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Multitrack render",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(baseAsset, overlayAsset),
            tracks = listOf(
                Track(
                    id = "base-track",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip(
                            id = "base-clip",
                            assetId = "base",
                            startUs = 0L,
                            durationUs = 4_000_000L,
                            sourceInUs = 0L,
                            sourceOutUs = 4_000_000L,
                        ),
                    ),
                ),
                Track(
                    id = "overlay-track",
                    type = TrackType.VISUAL,
                    order = 1,
                    collisionPolicy = TrackCollisionPolicy.OVERLAY,
                    clips = listOf(
                        Clip(
                            id = "overlay-clip",
                            assetId = "overlay",
                            startUs = 1_000_000L,
                            durationUs = 2_000_000L,
                            sourceInUs = 0L,
                            sourceOutUs = 2_000_000L,
                        ),
                    ),
                ),
                Track(
                    id = "audio-1",
                    type = TrackType.AUDIO,
                    order = 2,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                ),
            ),
        )
    }
}
