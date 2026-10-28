package com.localcut.editor.media

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCompositionRenderPlanIntegrationTest {
    private val resolver = RenderAssetResolver { asset -> Result.success("resolved://${asset.id}") }

    @Test
    fun parentingIsFlattenedIntoVisualRenderSegmentForPreviewAndExportPipeline() {
        val asset = videoAsset()
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
            transform = TransformValue(x = 10.0, y = 5.0, scale = 2.0, rotationDeg = 5.0, opacity = 0.8),
            opacity = 0.75,
        )
        val project = project(
            asset = asset,
            clip = clip,
            graph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(
                        id = "parent",
                        kind = CompositionNodeKind.NULL,
                        transform = LayerTransform3D(x = 100.0, y = 50.0, scale = 2.0, opacity = 0.5),
                    ),
                    CompositionNode(
                        id = "layer",
                        kind = CompositionNodeKind.LAYER,
                        parentId = "parent",
                        clipId = clip.id,
                        transform = LayerTransform3D(x = 25.0, y = 10.0, scale = 0.5, rotationZDeg = 10.0, opacity = 0.8),
                    ),
                ),
            ),
        )

        val plan = ProjectRenderPlanCompiler.compile(project, RenderMode.EXPORT, resolver).getOrThrow()
        val media = plan.visualLayers.single().segments.filterIsInstance<RenderSegment.Media>().single()

        assertEquals(158.9798366418, media.transform.x, 1e-6)
        assertEquals(76.6605205417, media.transform.y, 1e-6)
        assertEquals(2.0, media.transform.scale, 1e-6)
        assertEquals(15.0, media.transform.rotationDeg, 1e-6)
        assertEquals(0.30, media.opacity, 1e-9)
    }

    @Test
    fun staticCameraDepthProjectionReachesVisualRenderSegment() {
        val asset = videoAsset()
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
        )
        val project = project(
            asset = asset,
            clip = clip,
            graph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(
                        id = "camera",
                        kind = CompositionNodeKind.CAMERA,
                        camera = CameraSettings(focalLength = 1_000.0),
                    ),
                    CompositionNode(
                        id = "layer",
                        kind = CompositionNodeKind.LAYER,
                        clipId = clip.id,
                        transform = LayerTransform3D(x = 100.0, z = 100.0),
                    ),
                ),
            ),
        )

        val plan = ProjectRenderPlanCompiler.compile(project, RenderMode.FAST_PREVIEW, resolver).getOrThrow()
        val media = plan.visualLayers.single().segments.filterIsInstance<RenderSegment.Media>().single()

        assertTrue(media.transform.x > 100.0)
        assertTrue(media.transform.scale > 1.0)
    }

    private fun videoAsset() = Asset(
        id = "asset-1",
        type = AssetType.VIDEO,
        uri = "content://video/1",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 100L,
        displayName = "clip.mp4",
        mimeType = "video/mp4",
        fingerprint = "fingerprint-1",
    )

    private fun project(asset: Asset, clip: Clip, graph: CompositionGraph) = Project(
        id = "composition-render-plan",
        schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
        title = "Composition render plan",
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
        compositionGraph = graph,
    )
}
