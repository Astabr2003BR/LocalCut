package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxJobAwaitPolicyTest {
    @Test
    fun reverseStopsAtConfiguredPollLimitInsteadOfLoopingForever() = runBlocking {
        val fake = NeverFinishingJobClient()
        val outputRoot = Files.createTempDirectory("localcut-await-timeout").toFile()
        try {
            val coordinator = TermuxMediaReverseCoordinator(
                client = fake,
                sourceResolver = { asset ->
                    TermuxEngineMediaSource(asset.sizeBytes!!) { ByteArrayInputStream("abcde".toByteArray()) }
                },
                outputDirectory = outputRoot,
                awaitPolicy = TermuxJobAwaitPolicy(
                    maxPolls = 3,
                    initialDelayMs = 1,
                    maxDelayMs = 1,
                ),
                pollDelay = {},
            )

            val result = coordinator.reverse(videoAsset())

            assertTrue(result.isFailure)
            assertEquals("TERMUX_JOB_WAIT_TIMEOUT", result.exceptionOrNull()?.message)
            assertEquals(3, fake.getJobCalls)
            assertFalse(fake.downloaded)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    private fun videoAsset() = Asset(
        id = "video",
        type = AssetType.VIDEO,
        uri = "content://com.localcut.editor.remote-media/remote-media/video.mp4",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 5L,
        displayName = "video.mp4",
        mimeType = "video/mp4",
        fingerprint = "fp-timeout",
    )

    private class NeverFinishingJobClient : TermuxEngineJobClient {
        var getJobCalls = 0
        var downloaded = false
        private val jobId = "123e4567-e89b-12d3-a456-426614174999"

        override suspend fun pair(pairingCode: String) = error("unused")
        override suspend fun ping() = error("unused")
        override suspend fun getStatus() = error("unused")
        override suspend fun getCapabilities() = error("unused")

        override suspend fun createAnalyzeMediaJob(
            input: TermuxAnalyzeMediaInput,
            idempotencyKey: String,
        ): Result<TermuxEngineJob> = Result.success(
            TermuxEngineJob(jobId, "ANALYZE_MEDIA", TermuxEngineJobStatus.QUEUED, 0),
        )

        override suspend fun stageMedia(
            jobId: String,
            source: TermuxEngineMediaSource,
        ): Result<TermuxEngineStageResult> = Result.success(
            TermuxEngineStageResult(
                job = TermuxEngineJob(jobId, "ANALYZE_MEDIA", TermuxEngineJobStatus.RUNNING, 20),
                sizeBytes = source.contentLength,
                sha256 = "0".repeat(64),
            ),
        )

        override suspend fun getJob(jobId: String): Result<TermuxEngineJob> {
            getJobCalls += 1
            return Result.success(
                TermuxEngineJob(jobId, "ANALYZE_MEDIA", TermuxEngineJobStatus.RUNNING, 50),
            )
        }

        override suspend fun cancelJob(jobId: String) = error("unused")

        override suspend fun downloadGeneratedMedia(
            jobId: String,
            destination: File,
        ): Result<TermuxGeneratedMediaDownload> {
            downloaded = true
            error("download must not run for a timed-out job")
        }
    }
}
