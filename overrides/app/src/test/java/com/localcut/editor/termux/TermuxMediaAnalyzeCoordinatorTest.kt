package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
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
        assertEquals(TermuxAnalyzeMediaMode.MEDIA, fake.lastInput?.mode)
    }

    @Test
    fun trackingUsesSameSafeStreamPathAndTypedTrackingOptions() = runBlocking {
        val fake = FakeJobClient(trackingResult = true)
        var resolvedUri: String? = null
        val coordinator = TermuxMediaTrackingCoordinator(fake) { asset ->
            resolvedUri = asset.uri
            TermuxEngineMediaSource(asset.sizeBytes!!) { ByteArrayInputStream("abcde".toByteArray()) }
        }
        val asset = asset(sizeBytes = 5L)

        val result = coordinator.analyze(
            asset = asset,
            options = TermuxTrackingOptions(maxFrames = 120, sampleEvery = 3, maxCorners = 80),
        ).getOrThrow()

        assertEquals(asset.uri, resolvedUri)
        assertTrue(fake.staged)
        assertTrue(fake.idempotencyKey!!.matches(Regex("^analyze-tracking-v1:[0-9a-f]{64}$")))
        assertEquals(TermuxAnalyzeMediaMode.TRACKING, fake.lastInput?.mode)
        assertEquals(3, fake.lastInput?.trackingOptions?.sampleEvery)
        assertEquals(80, fake.lastInput?.trackingOptions?.maxCorners)
        assertEquals(7, result.framesDecoded)
        assertEquals(100_000L, result.samples.single().timeUs)
    }

    @Test
    fun reverseUsesSafeStreamAndDownloadsGeneratedMp4IntoPrivateDirectory() = runBlocking {
        val fake = FakeJobClient()
        var resolvedUri: String? = null
        val outputRoot = Files.createTempDirectory("localcut-reverse-coordinator").toFile()
        try {
            val coordinator = TermuxMediaReverseCoordinator(
                client = fake,
                sourceResolver = { asset ->
                    resolvedUri = asset.uri
                    TermuxEngineMediaSource(asset.sizeBytes!!) { ByteArrayInputStream("abcde".toByteArray()) }
                },
                outputDirectory = outputRoot,
            )
            val asset = asset(sizeBytes = 5L)

            val result = coordinator.reverse(
                asset = asset,
                options = TermuxReverseOptions(chunkDurationUs = 1_000_000L),
            ).getOrThrow()

            assertEquals(asset.uri, resolvedUri)
            assertTrue(fake.staged)
            assertTrue(fake.downloaded)
            assertTrue(fake.idempotencyKey!!.matches(Regex("^reverse-media-v1:[0-9a-f]{64}$")))
            assertEquals(TermuxAnalyzeMediaMode.REVERSE, fake.lastInput?.mode)
            assertEquals(1_000_000L, fake.lastInput?.reverseOptions?.chunkDurationUs)
            assertEquals(outputRoot.canonicalFile, result.file.parentFile.canonicalFile)
            assertEquals("reversed", result.file.readText())
            assertEquals("video/mp4", result.media.mimeType)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun trackingRejectsNonVideoBeforeOpeningSource() = runBlocking {
        var resolved = false
        val coordinator = TermuxMediaTrackingCoordinator(FakeJobClient()) {
            resolved = true
            error("must not resolve")
        }
        val result = coordinator.analyze(asset(sizeBytes = 5L).copy(type = AssetType.AUDIO, mimeType = "audio/mp4"))
        assertTrue(result.isFailure)
        assertEquals("TERMUX_TRACKING_REQUIRES_VIDEO", result.exceptionOrNull()?.message)
        assertFalse(resolved)
    }

    @Test
    fun reverseRejectsNonVideoBeforeOpeningSourceOrCreatingOutput() = runBlocking {
        var resolved = false
        val outputRoot = Files.createTempDirectory("localcut-reverse-reject").toFile()
        try {
            val coordinator = TermuxMediaReverseCoordinator(
                client = FakeJobClient(),
                sourceResolver = {
                    resolved = true
                    error("must not resolve")
                },
                outputDirectory = outputRoot,
            )
            val result = coordinator.reverse(
                asset(sizeBytes = 5L).copy(type = AssetType.AUDIO, mimeType = "audio/mp4"),
            )
            assertTrue(result.isFailure)
            assertEquals("TERMUX_REVERSE_REQUIRES_VIDEO", result.exceptionOrNull()?.message)
            assertFalse(resolved)
            assertTrue(outputRoot.listFiles().orEmpty().isEmpty())
        } finally {
            outputRoot.deleteRecursively()
        }
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

    private class FakeJobClient(private val trackingResult: Boolean = false) : TermuxEngineJobClient {
        var staged = false
        var downloaded = false
        var idempotencyKey: String? = null
        var lastInput: TermuxAnalyzeMediaInput? = null
        override suspend fun pair(pairingCode: String) = error("unused")
        override suspend fun ping() = error("unused")
        override suspend fun getStatus() = error("unused")
        override suspend fun getCapabilities() = error("unused")
        override suspend fun createAnalyzeMediaJob(
            input: TermuxAnalyzeMediaInput,
            idempotencyKey: String,
        ): Result<TermuxEngineJob> {
            this.idempotencyKey = idempotencyKey
            this.lastInput = input
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
            val job = if (trackingResult) {
                TermuxEngineJob(
                    id = jobId,
                    type = "ANALYZE_MEDIA",
                    status = TermuxEngineJobStatus.SUCCEEDED,
                    progress = 100,
                    analysisMode = "opencv-global-motion-v1",
                    globalMotion = TermuxGlobalMotionAnalysis(
                        framesDecoded = 7,
                        frameRate = 30.0,
                        samples = listOf(
                            TermuxGlobalMotionSample(
                                frameIndex = 3,
                                timeUs = 100_000L,
                                dx = 2.0,
                                dy = 1.0,
                                rotationDeg = 1.0,
                                scale = 1.01,
                                confidence = 0.9,
                            ),
                        ),
                    ),
                )
            } else {
                TermuxEngineJob(jobId, "ANALYZE_MEDIA", TermuxEngineJobStatus.SUCCEEDED, 100)
            }
            return Result.success(
                TermuxEngineStageResult(
                    job = job,
                    sizeBytes = source.contentLength,
                    sha256 = "0".repeat(64),
                ),
            )
        }

        override suspend fun downloadGeneratedMedia(
            jobId: String,
            destination: File,
        ): Result<TermuxGeneratedMediaDownload> {
            downloaded = true
            val bytes = "reversed".toByteArray()
            destination.parentFile?.mkdirs()
            destination.writeBytes(bytes)
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            return Result.success(
                TermuxGeneratedMediaDownload(
                    job = TermuxEngineJob(jobId, "ANALYZE_MEDIA", TermuxEngineJobStatus.SUCCEEDED, 100),
                    media = TermuxGeneratedMediaMetadata(bytes.size.toLong(), sha, "video/mp4"),
                    file = destination,
                ),
            )
        }
    }
}
