package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxMediaAnalyzeCoordinatorTest {
    @Test
    fun selectedAssetIsStreamedWithoutExposingPrivatePath() = runBlocking {
        val fake = FakeJobClient()
        var resolvedUri: String? = null
        val coordinator = TermuxMediaAnalyzeCoordinator(fake) { asset ->
            resolvedUri = asset.uri
            TermuxEngineMediaSource(asset.sizeBytes!!) { ByteArrayInputStream("abcde".toByteArray()) }
        }
        val asset = asset(sizeBytes = 5L)
        val result = coordinator.analyze(asset).getOrThrow()
        assertEquals(TermuxEngineJobStatus.SUCCEEDED, result.status)
        assertTrue(fake.staged)
        assertEquals(asset.uri, resolvedUri)
        assertTrue(fake.idempotencyKey!!.matches(Regex("^analyze-media-v1:[0-9a-f]{64}$")))
        assertFalse(fake.idempotencyKey!!.contains(asset.displayName))
    }

    @Test
    fun unknownMediaSizeFailsBeforeOpeningSource() = runBlocking {
        var resolved = false
        val coordinator = TermuxMediaAnalyzeCoordinator(FakeJobClient()) {
            resolved = true
            error("must not resolve")
        }
        val result = coordinator.analyze(asset(sizeBytes = null))
        assertTrue(result.isFailure)
        assertEquals("TERMUX_MEDIA_SIZE_UNAVAILABLE", result.exceptionOrNull()?.message)
        assertFalse(resolved)
    }

    private fun asset(sizeBytes: Long?) = Asset(
        id = "asset-1",
        type = AssetType.VIDEO,
        uri = "content://com.localcut.editor.remote-media/remote-media/asset-1.mp4",
        sourceDurationUs = 2_000_000L,
        sizeBytes = sizeBytes,
        displayName = "vídeo teste.mp4",
        mimeType = "video/mp4",
        fingerprint = "fp-1",
    )

    private class FakeJobClient : TermuxEngineJobClient {
        var staged = false
        var idempotencyKey: String? = null
        override suspend fun pair(pairingCode: String) = error("unused")
        override suspend fun ping() = error("unused")
        override suspend fun getStatus() = error("unused")
        override suspend fun getCapabilities() = error("unused")
        override suspend fun createAnalyzeMediaJob(
            input: TermuxAnalyzeMediaInput,
            idempotencyKey: String,
        ): Result<TermuxEngineJob> {
            this.idempotencyKey = idempotencyKey
            return Result.success(
                TermuxEngineJob(
                    id = "123e4567-e89b-12d3-a456-426614174000",
                    type = "ANALYZE_MEDIA",
                    status = TermuxEngineJobStatus.QUEUED,
                    progress = 0,
                ),
            )
        }
        override suspend fun getJob(jobId: String) = error("unused")
        override suspend fun cancelJob(jobId: String) = error("unused")
        override suspend fun stageMedia(
            jobId: String,
            source: TermuxEngineMediaSource,
        ): Result<TermuxEngineStageResult> {
            staged = true
            return Result.success(
                TermuxEngineStageResult(
                    job = TermuxEngineJob(jobId, "ANALYZE_MEDIA", TermuxEngineJobStatus.SUCCEEDED, 100),
                    sizeBytes = source.contentLength,
                    sha256 = "0".repeat(64),
                ),
            )
        }
    }
}
