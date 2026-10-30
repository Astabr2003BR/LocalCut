package com.localcut.editor.termux

import java.io.ByteArrayInputStream
import java.io.File
import java.net.ConnectException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveringTermuxEngineJobClientTest {
    @Test
    fun safeReadWakesAndRetriesExactlyOnce() = runBlocking {
        val delegate = FakeClient().apply { failFirstPing = true }
        var wakes = 0
        val client = RecoveringTermuxEngineJobClient(
            delegate = delegate,
            wakeAction = { wakes += 1; Result.success(true) },
            retryDelayMs = 0L,
        )

        assertTrue(client.ping().isSuccess)
        assertEquals(2, delegate.pingCalls)
        assertEquals(1, wakes)
    }

    @Test
    fun idempotentCreateJobCanRetryWithSameRequestKey() = runBlocking {
        val delegate = FakeClient().apply { failFirstCreate = true }
        var wakes = 0
        val client = RecoveringTermuxEngineJobClient(
            delegate = delegate,
            wakeAction = { wakes += 1; Result.success(true) },
            retryDelayMs = 0L,
        )
        val input = TermuxAnalyzeMediaInput(
            assetId = "asset-1",
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1L,
            sourceDurationUs = 1_000_000L,
        )

        assertTrue(client.createAnalyzeMediaJob(input, "stable-key").isSuccess)
        assertEquals(2, delegate.createCalls)
        assertEquals(1, wakes)
    }

    @Test
    fun mediaStageIsNeverRetriedAutomatically() = runBlocking {
        val delegate = FakeClient().apply { failStage = true }
        var wakes = 0
        val client = RecoveringTermuxEngineJobClient(
            delegate = delegate,
            wakeAction = { wakes += 1; Result.success(true) },
            retryDelayMs = 0L,
        )
        val source = TermuxEngineMediaSource(1L) { ByteArrayInputStream(byteArrayOf(1)) }

        assertTrue(client.stageMedia("job-1", source).isFailure)
        assertEquals(1, delegate.stageCalls)
        assertEquals(0, wakes)
    }

    private class FakeClient : TermuxEngineJobClient {
        var pingCalls = 0
        var createCalls = 0
        var stageCalls = 0
        var failFirstPing = false
        var failFirstCreate = false
        var failStage = false

        override suspend fun pair(pairingCode: String) = Result.success(TermuxEnginePairing(1, "x".repeat(32)))

        override suspend fun ping(): Result<TermuxEnginePing> {
            pingCalls += 1
            if (failFirstPing && pingCalls == 1) return Result.failure(ConnectException("offline"))
            return Result.success(TermuxEnginePing(1, "READY"))
        }

        override suspend fun getStatus() = Result.success(
            TermuxEngineStatus(1, "0.5.0", "localcut", "READY", 1L),
        )

        override suspend fun getCapabilities() = Result.success(
            TermuxEngineCapabilities(1, TermuxAutomationPolicy.REQUIRED_CREATIVE_CAPABILITIES),
        )

        override suspend fun createAnalyzeMediaJob(
            input: TermuxAnalyzeMediaInput,
            idempotencyKey: String,
        ): Result<TermuxEngineJob> {
            createCalls += 1
            if (failFirstCreate && createCalls == 1) {
                return Result.failure(IllegalStateException("TERMUX_ENGINE_UNAVAILABLE"))
            }
            return Result.success(job())
        }

        override suspend fun getJob(jobId: String) = Result.success(job())
        override suspend fun cancelJob(jobId: String) = Result.success(job())

        override suspend fun stageMedia(
            jobId: String,
            source: TermuxEngineMediaSource,
        ): Result<TermuxEngineStageResult> {
            stageCalls += 1
            if (failStage) return Result.failure(ConnectException("offline"))
            return Result.success(TermuxEngineStageResult(job(), 1L, "a".repeat(64)))
        }

        override suspend fun downloadGeneratedMedia(
            jobId: String,
            destination: File,
        ): Result<TermuxGeneratedMediaDownload> = Result.failure(UnsupportedOperationException())

        private fun job() = TermuxEngineJob(
            id = "job-1",
            type = "ANALYZE_MEDIA",
            status = TermuxEngineJobStatus.QUEUED,
            progress = 0,
        )
    }
}
