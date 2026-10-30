package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxMediaObjectTrackingCoordinatorTest {
    @Test
    fun objectTrackingUsesSafeStreamAndTrimBoundIdempotencyKey() = runBlocking {
        val fake = FakeObjectTrackingClient()
        var resolvedUri: String? = null
        val coordinator = TermuxMediaObjectTrackingCoordinator(
            client = fake,
            sourceResolver = { asset ->
                resolvedUri = asset.uri
                TermuxEngineMediaSource(asset.sizeBytes!!) { ByteArrayInputStream("abcde".toByteArray()) }
            },
        )
        val asset = videoAsset()
        val options = TermuxObjectTrackingOptions(
            roi = TermuxObjectTrackingRoi(0.1, 0.2, 0.3, 0.4),
            sourceInUs = 250_000L,
            sourceOutUs = 1_500_000L,
            maxFrames = 120,
            sampleEvery = 2,
            maxCorners = 80,
        )

        val result = coordinator.analyze(asset, options).getOrThrow()
        val firstKey = fake.idempotencyKeys.single()
        coordinator.analyze(
            asset,
            options.copy(sourceInUs = 300_000L, sourceOutUs = 1_500_000L),
        ).getOrThrow()
        val secondKey = fake.idempotencyKeys.last()

        assertEquals(asset.uri, resolvedUri)
        assertTrue(fake.staged)
        assertTrue(firstKey.matches(Regex("^object-tracking-v1:[0-9a-f]{64}$")))
        assertNotEquals(firstKey, secondKey)
        assertEquals(TermuxAnalyzeMediaMode.OBJECT_TRACKING, fake.lastInput?.mode)
        assertEquals(300_000L, fake.lastInput?.objectTrackingOptions?.sourceInUs)
        assertEquals(1_500_000L, fake.lastInput?.objectTrackingOptions?.sourceOutUs)
        assertEquals(320, result.frameWidth)
        assertEquals(33_333L, result.samples.single().timeUs)
    }

    @Test
    fun objectTrackingRejectsNonVideoBeforeResolvingSource() = runBlocking {
        var resolved = false
        val coordinator = TermuxMediaObjectTrackingCoordinator(
            client = FakeObjectTrackingClient(),
            sourceResolver = {
                resolved = true
                error("must not resolve")
            },
        )
        val audio = videoAsset().copy(type = AssetType.AUDIO, mimeType = "audio/mp4")

        val result = coordinator.analyze(
            audio,
            TermuxObjectTrackingOptions(TermuxObjectTrackingRoi(0.1, 0.1, 0.2, 0.2)),
        )

        assertTrue(result.isFailure)
        assertEquals("TERMUX_OBJECT_TRACKING_REQUIRES_VIDEO", result.exceptionOrNull()?.message)
        assertFalse(resolved)
    }

    private fun videoAsset() = Asset(
        id = "asset-1",
        type = AssetType.VIDEO,
        uri = "content://com.localcut.editor.remote-media/remote-media/asset-1.mp4",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 5L,
        displayName = "clip.mp4",
        mimeType = "video/mp4",
        fingerprint = "fp-1",
    )

    private class FakeObjectTrackingClient : TermuxEngineJobClient {
        var staged = false
        val idempotencyKeys = mutableListOf<String>()
        var lastInput: TermuxAnalyzeMediaInput? = null
        private val jobId = "123e4567-e89b-12d3-a456-426614174000"

        override suspend fun pair(pairingCode: String) = error("unused")
        override suspend fun ping() = error("unused")
        override suspend fun getStatus() = error("unused")
        override suspend fun getCapabilities() = error("unused")
        override suspend fun cancelJob(jobId: String) = error("unused")
        override suspend fun getJob(jobId: String) = error("unused")

        override suspend fun createAnalyzeMediaJob(
            input: TermuxAnalyzeMediaInput,
            idempotencyKey: String,
        ): Result<TermuxEngineJob> {
            lastInput = input
            idempotencyKeys += idempotencyKey
            return Result.success(TermuxEngineJob(jobId, "ANALYZE_MEDIA", TermuxEngineJobStatus.QUEUED, 0))
        }

        override suspend fun stageMedia(
            jobId: String,
            source: TermuxEngineMediaSource,
        ): Result<TermuxEngineStageResult> {
            staged = true
            val tracking = TermuxObjectTrackingAnalysis(
                framesDecoded = 4,
                frameWidth = 320,
                frameHeight = 240,
                frameRate = 30.0,
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
            return Result.success(
                TermuxEngineStageResult(
                    job = TermuxEngineJob(
                        id = jobId,
                        type = "ANALYZE_MEDIA",
                        status = TermuxEngineJobStatus.SUCCEEDED,
                        progress = 100,
                        analysisMode = "opencv-object-position-v1",
                        objectTracking = tracking,
                    ),
                    sizeBytes = source.contentLength,
                    sha256 = "0".repeat(64),
                ),
            )
        }
    }
}
