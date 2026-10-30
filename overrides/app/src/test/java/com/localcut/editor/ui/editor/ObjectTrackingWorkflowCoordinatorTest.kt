package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.termux.TermuxObjectTrackingAnalysis
import com.localcut.editor.termux.TermuxObjectTrackingOptions
import com.localcut.editor.termux.TermuxObjectTrackingSample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingWorkflowCoordinatorTest {
    @Test
    fun previewSelectionUsesCanonicalParentTransformBeforeStartingOneAnalysis() = runBlocking {
        var currentProject = project(parentX = 100.0)
        val geometry = FakeGeometryGateway(Result.success(ObjectTrackingSourceGeometry(1000, 500)))
        val analysis = FakeAnalysisGateway { options -> trackingResult(options) }
        val action = ObjectTrackingActionCoordinator(
            analysisGateway = analysis,
            currentClipProvider = { id -> currentProject.findClip(id) },
        )
        val workflow = ObjectTrackingWorkflowCoordinator(
            geometryGateway = geometry,
            actionCoordinator = action,
            currentProjectProvider = { currentProject },
        )

        val command = workflow.buildCommand(
            clipId = "clip-1",
            selection = PreviewSelectionRect(200.0, 100.0, 300.0, 200.0),
            viewportWidthPx = 1000,
            viewportHeightPx = 500,
            playheadUs = 500_000L,
        ).getOrThrow()

        assertEquals("clip-1", command.clipId)
        assertEquals(1, geometry.calls)
        assertEquals(1, analysis.calls)
        val roi = requireNotNull(analysis.lastOptions).roi
        assertEquals(0.10, roi.x, 0.000001)
        assertEquals(0.20, roi.y, 0.000001)
        assertEquals(0.10, roi.width, 0.000001)
        assertEquals(0.20, roi.height, 0.000001)
    }

    @Test
    fun playheadOutsideSelectedClipFailsBeforeMetadataOrOpenCv() = runBlocking {
        val currentProject = project(parentX = 0.0)
        val geometry = FakeGeometryGateway(Result.success(ObjectTrackingSourceGeometry(1000, 500)))
        val analysis = FakeAnalysisGateway { options -> trackingResult(options) }
        val workflow = ObjectTrackingWorkflowCoordinator(
            geometryGateway = geometry,
            actionCoordinator = ObjectTrackingActionCoordinator(
                analysisGateway = analysis,
                currentClipProvider = { id -> currentProject.findClip(id) },
            ),
            currentProjectProvider = { currentProject },
        )

        val result = workflow.buildCommand(
            clipId = "clip-1",
            selection = PreviewSelectionRect(100.0, 100.0, 200.0, 200.0),
            viewportWidthPx = 1000,
            viewportHeightPx = 500,
            playheadUs = 1_000_000L,
        )

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_PLAYHEAD_OUTSIDE_CLIP", result.exceptionOrNull()?.message)
        assertEquals(0, geometry.calls)
        assertEquals(0, analysis.calls)
    }

    @Test
    fun geometryFailurePreventsOpenCvJob() = runBlocking {
        val currentProject = project(parentX = 0.0)
        val geometry = FakeGeometryGateway(Result.failure(IllegalArgumentException("OBJECT_TRACKING_SOURCE_WIDTH_UNAVAILABLE")))
        val analysis = FakeAnalysisGateway { options -> trackingResult(options) }
        val workflow = ObjectTrackingWorkflowCoordinator(
            geometryGateway = geometry,
            actionCoordinator = ObjectTrackingActionCoordinator(
                analysisGateway = analysis,
                currentClipProvider = { id -> currentProject.findClip(id) },
            ),
            currentProjectProvider = { currentProject },
        )

        val result = workflow.buildCommand(
            clipId = "clip-1",
            selection = PreviewSelectionRect(100.0, 100.0, 200.0, 200.0),
            viewportWidthPx = 1000,
            viewportHeightPx = 500,
            playheadUs = 500_000L,
        )

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_SOURCE_WIDTH_UNAVAILABLE", result.exceptionOrNull()?.message)
        assertEquals(1, geometry.calls)
        assertEquals(0, analysis.calls)
    }

    @Test
    fun compositionGraphChangeDuringAnalysisDiscardsCommand() = runBlocking {
        var currentProject = project(parentX = 0.0)
        val geometry = FakeGeometryGateway(Result.success(ObjectTrackingSourceGeometry(1000, 500)))
        val analysis = FakeAnalysisGateway { options ->
            currentProject = currentProject.copy(compositionGraph = graph(parentX = 180.0))
            trackingResult(options)
        }
        val workflow = ObjectTrackingWorkflowCoordinator(
            geometryGateway = geometry,
            actionCoordinator = ObjectTrackingActionCoordinator(
                analysisGateway = analysis,
                currentClipProvider = { id -> currentProject.findClip(id) },
            ),
            currentProjectProvider = { currentProject },
        )

        val result = workflow.buildCommand(
            clipId = "clip-1",
            selection = PreviewSelectionRect(100.0, 100.0, 200.0, 200.0),
            viewportWidthPx = 1000,
            viewportHeightPx = 500,
            playheadUs = 500_000L,
        )

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_VISUAL_STATE_STALE", result.exceptionOrNull()?.message)
        assertEquals(1, analysis.calls)
    }

    private fun project(parentX: Double): Project {
        val asset = videoAsset()
        val clip = videoClip()
        return Project.create("Tracking", 1000, 500, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            compositionGraph = graph(parentX),
        )
    }

    private fun graph(parentX: Double) = CompositionGraph(
        nodes = listOf(
            CompositionNode(
                id = "group-1",
                kind = CompositionNodeKind.GROUP,
                transform = LayerTransform3D(x = parentX),
            ),
            CompositionNode(
                id = "layer-1",
                kind = CompositionNodeKind.LAYER,
                parentId = "group-1",
                clipId = "clip-1",
            ),
        ),
    )

    private fun videoAsset() = Asset(
        id = "asset-1",
        type = AssetType.VIDEO,
        uri = "content://video",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 5L,
        displayName = "clip.mp4",
        mimeType = "video/mp4",
        fingerprint = "fp-1",
    )

    private fun videoClip() = Clip(
        id = "clip-1",
        assetId = "asset-1",
        startUs = 0L,
        durationUs = 1_000_000L,
        sourceInUs = 0L,
        sourceOutUs = 1_000_000L,
    )

    private fun trackingResult(options: TermuxObjectTrackingOptions) = TermuxObjectTrackingAnalysis(
        framesDecoded = 4,
        frameWidth = 1000,
        frameHeight = 500,
        frameRate = 30.0,
        sourceInUs = options.sourceInUs,
        sourceOutUs = options.sourceOutUs,
        samples = listOf(
            TermuxObjectTrackingSample(
                frameIndex = 1,
                timeUs = 33_333L,
                dxNorm = 0.01,
                dyNorm = 0.0,
                centerXNorm = 0.2,
                centerYNorm = 0.3,
                confidence = 0.9,
            ),
        ),
    )

    private class FakeGeometryGateway(
        private val result: Result<ObjectTrackingSourceGeometry>,
    ) : ObjectTrackingGeometryGateway {
        var calls: Int = 0

        override suspend fun resolve(asset: Asset): Result<ObjectTrackingSourceGeometry> {
            calls += 1
            return result
        }
    }

    private class FakeAnalysisGateway(
        private val result: (TermuxObjectTrackingOptions) -> TermuxObjectTrackingAnalysis,
    ) : ObjectTrackingAnalysisGateway {
        var calls: Int = 0
        var lastOptions: TermuxObjectTrackingOptions? = null

        override suspend fun analyze(
            asset: Asset,
            options: TermuxObjectTrackingOptions,
        ): Result<TermuxObjectTrackingAnalysis> {
            calls += 1
            lastOptions = options
            return Result.success(result(options))
        }
    }
}
