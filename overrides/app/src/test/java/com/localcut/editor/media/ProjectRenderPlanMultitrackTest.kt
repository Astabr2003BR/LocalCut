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
        assertEquals("base-track", plan.visualLayers[0].sourceTrackId)
        assertEquals("overlay-track", plan.visualLayers[1].sourceTrackId)
        assertEquals(TrackCollisionPolicy.OVERLAY, plan.visualLayers[1].sourceCollisionPolicy)

        assertEquals(
            listOf(1_000_000L, 2_000_000L, 1_000_000L),
            plan.visualLayers[1].segments.map { it.durationUs },
        )
        assertTrue(plan.visualLayers[1].segments.first() is RenderSegment.Gap)
        assertTrue(plan.visualLayers[1].segments.last() is RenderSegment.Gap)
    }

    @Test
    fun overlappingClipsOnOneOverlayTrackArePartitionedIntoDeterministicLinearLanes() {
        val base = twoVisualTrackProject()
        val project = base.copy(
            tracks = base.tracks.map { track ->
                if (track.id == "overlay-track") {
                    track.copy(
                        clips = listOf(
                            Clip("overlay-a", "overlay", 500_000L, 2_000_000L, 0L, 2_000_000L),
                            Clip("overlay-b", "overlay", 1_000_000L, 2_000_000L, 0L, 2_000_000L),
                        ),
                    )
                } else {
                    track
                }
            },
        )

        val plan = ProjectRenderPlanCompiler.compile(project, RenderMode.EXPORT, resolver).getOrThrow()
        val overlayLayers = plan.visualLayers.filter { it.sourceTrackId == "overlay-track" }

        assertEquals(2, overlayLayers.size)
        assertEquals(listOf(0, 1), overlayLayers.map { it.laneIndex })
        assertEquals(
            listOf("overlay-a", "overlay-b"),
            overlayLayers.map { layer -> layer.segments.filterIsInstance<RenderSegment.Media>().single().clipId },
        )
        assertTrue(overlayLayers.all { it.sourceCollisionPolicy == TrackCollisionPolicy.OVERLAY })
    }

    @Test
    fun multipleExclusiveAudioTracksCompileIntoOrderedMixingLayers() {
        val base = twoVisualTrackProject()
        val extraAudio = Track(
            id = "audio-2",
            type = TrackType.AUDIO,
            order = 3,
            collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
        )
        val project = base.copy(tracks = base.tracks + extraAudio)

        val plan = ProjectRenderPlanCompiler.compile(project, RenderMode.EXPORT, resolver).getOrThrow()

        assertEquals(listOf("audio-1", "audio-2"), plan.audioLayers.map { it.sourceTrackId })
        assertTrue(plan.audioLayers.all { it.sourceCollisionPolicy == TrackCollisionPolicy.EXCLUSIVE })
    }

    @Test
    fun overlayAudioInsideOneTrackRemainsRejectedUntilLaneMixingIsDefined() {
        val base = twoVisualTrackProject()
        val project = base.copy(
            tracks = base.tracks.map { track ->
                if (track.id == "audio-1") track.copy(collisionPolicy = TrackCollisionPolicy.OVERLAY) else track
            },
        )

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
