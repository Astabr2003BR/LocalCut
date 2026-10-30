package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.termux.TermuxObjectTrackingAnalysis
import com.localcut.editor.termux.TermuxObjectTrackingOptions
import com.localcut.editor.termux.TermuxObjectTrackingSample
import com.localcut.editor.termux.TermuxTrackingQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingActionCoordinatorTest {
    @Test
    fun successUsesRoiTrimQualityAndRechecksCurrentClipAfterAnalysis() = runBlocking {
        val asset = videoAsset()
        var currentClip = videoClip()
        val gateway = FakeGateway { options ->
            trackingResult(options, frameWidth = 1000, frameHeight = 500)
        }
        val coordinator = ObjectTrackingActionCoordinator(
            analysisGateway = gateway,
            currentClipProvider = { id -> currentClip.takeIf { it.id == id } },
        )
        val captured = ObjectTrackingApplyGuard.capture(currentClip)

        val command = coordinator.buildCommand(
            asset = asset,
            captured = captured,
            roi = NormalizedMediaRoi(0.1, 0.2, 0.3, 0.4),
            projectWidth = 1000,
            projectHeight = 500,
            quality = TermuxTrackingQuality.FAST,
        ).getOrThrow()

        assertEquals(1, gateway.calls)
        val options = gateway.lastOptions!!
        assertEquals(0.1, options.roi.x, 0.0)
        assertEquals(0L, options.sourceInUs)
        assertEquals(1_000_000L, options.sourceOutUs)
        assertEquals(120, options.maxFrames)
        assertEquals(4, options.sampleEvery)
        assertEquals(80, options.maxCorners)
        assertEquals("clip-1", command.clipId)
        assertTrue(ObjectTrackingUiPolicy.hasGeneratedObjectTracking(command.propertyAnimations))
    }

    @Test
    fun staleClipAfterAnalysisRejectsResultInsteadOfOverwritingEdit() = runBlocking {
        val asset = videoAsset()
        var currentClip = videoClip()
        val captured = ObjectTrackingApplyGuard.capture(currentClip)
        val gateway = FakeGateway { options ->
            currentClip = currentClip.copy(sourceInUs = 100_000L, sourceOutUs = 1_000_000L)
            trackingResult(options, frameWidth = 1000, frameHeight = 500)
        }
        val coordinator = ObjectTrackingActionCoordinator(
            analysisGateway = gateway,
            currentClipProvider = { currentClip },
        )

        val result = coordinator.buildCommand(
            asset = asset,
            captured = captured,
            roi = NormalizedMediaRoi(0.1, 0.2, 0.3, 0.4),
            projectWidth = 1000,
            projectHeight = 500,
        )

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_RESULT_STALE", result.exceptionOrNull()?.message)
        assertEquals(1, gateway.calls)
    }

    @Test
    fun invalidPreflightNeverStartsExpensiveTermuxJob() = runBlocking {
        val asset = videoAsset()
        val reverseClip = videoClip().copy(timeMap = TimeMap(reverse = true))
        val gateway = FakeGateway { error("must not run") }
        val coordinator = ObjectTrackingActionCoordinator(
            analysisGateway = gateway,
            currentClipProvider = { reverseClip },
        )

        val result = coordinator.buildCommand(
            asset = asset,
            captured = ObjectTrackingApplyGuard.capture(reverseClip),
            roi = NormalizedMediaRoi(0.1, 0.2, 0.3, 0.4),
            projectWidth = 1000,
            projectHeight = 500,
        )

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_REVERSE_UNSUPPORTED", result.exceptionOrNull()?.message)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun wrongAssetFailsBeforeGateway() = runBlocking {
        val clip = videoClip()
        val gateway = FakeGateway { error("must not run") }
        val coordinator = ObjectTrackingActionCoordinator(
            analysisGateway = gateway,
            currentClipProvider = { clip },
        )

        val result = coordinator.buildCommand(
            asset = videoAsset().copy(id = "other-asset"),
            captured = ObjectTrackingApplyGuard.capture(clip),
            roi = NormalizedMediaRoi(0.1, 0.2, 0.3, 0.4),
            projectWidth = 1000,
            projectHeight = 500,
        )

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_ASSET_MISMATCH", result.exceptionOrNull()?.message)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun deletedClipAfterAnalysisIsStale() = runBlocking {
        val asset = videoAsset()
        var currentClip: Clip? = videoClip()
        val captured = ObjectTrackingApplyGuard.capture(requireNotNull(currentClip))
        val gateway = FakeGateway { options ->
            currentClip = null
            trackingResult(options, frameWidth = 1000, frameHeight = 500)
        }
        val coordinator = ObjectTrackingActionCoordinator(
            analysisGateway = gateway,
            currentClipProvider = { currentClip },
        )

        val result = coordinator.buildCommand(
            asset = asset,
            captured = captured,
            roi = NormalizedMediaRoi(0.1, 0.2, 0.3, 0.4),
            projectWidth = 1000,
            projectHeight = 500,
        )

        assertTrue(result.isFailure)
        assertEquals("OBJECT_TRACKING_RESULT_STALE", result.exceptionOrNull()?.message)
        assertFalse(result.isSuccess)
    }

    private fun trackingResult(
        options: TermuxObjectTrackingOptions,
        frameWidth: Int,
        frameHeight: Int,
    ) = TermuxObjectTrackingAnalysis(
        framesDecoded = 4,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        frameRate = 30.0,
        sourceInUs = options.sourceInUs,
        sourceOutUs = options.sourceOutUs,
        samples = listOf(
            TermuxObjectTrackingSample(
                frameIndex = 1,
                timeUs = 33_333L,
                dxNorm = 0.01,
                dyNorm = 0.02,
                centerXNorm = 0.45,
                centerYNorm = 0.55,
                confidence = 0.9,
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

    private class FakeGateway(
        private val result: (TermuxObjectTrackingOptions) -> TermuxObjectTrackingAnalysis,
    ) : ObjectTrackingAnalysisGateway {
        var calls = 0
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
