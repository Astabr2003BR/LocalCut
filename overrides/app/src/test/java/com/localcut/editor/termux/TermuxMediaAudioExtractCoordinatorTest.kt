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

class TermuxMediaAudioExtractCoordinatorTest {
    @Test
    fun invalidDownloadedMimeRemovesOwnedDestination() = runBlocking {
        val outputRoot = Files.createTempDirectory("localcut-audio-invalid-mime").toFile()
        try {
            val coordinator = coordinator(
                outputRoot = outputRoot,
                downloadFactory = { jobId, destination ->
                    destination.writeText("invalid-audio")
                    Result.success(
                        TermuxGeneratedMediaDownload(
                            job = succeededJob(jobId),
                            media = TermuxGeneratedMediaMetadata(
                                sizeBytes = destination.length(),
                                sha256 = "0".repeat(64),
                                mimeType = "video/mp4",
                            ),
                            file = destination,
                        ),
                    )
                },
            )

            val result = coordinator.extract(videoAsset())

            assertTrue(result.isFailure)
            assertEquals("TERMUX_EXTRACT_AUDIO_DOWNLOAD_TYPE_INVALID", result.exceptionOrNull()?.message)
            assertTrue(outputRoot.listFiles().orEmpty().isEmpty())
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun mismatchedReturnedPathRemovesOnlyOwnedDestination() = runBlocking {
        val outputRoot = Files.createTempDirectory("localcut-audio-path-mismatch").toFile()
        val outsideFile = Files.createTempFile("localcut-audio-outside", ".m4a").toFile().apply {
            writeText("outside")
        }
        try {
            val coordinator = coordinator(
                outputRoot = outputRoot,
                downloadFactory = { jobId, destination ->
                    destination.writeText("owned")
                    Result.success(
                        TermuxGeneratedMediaDownload(
                            job = succeededJob(jobId),
                            media = TermuxGeneratedMediaMetadata(
                                sizeBytes = destination.length(),
                                sha256 = "0".repeat(64),
                                mimeType = "audio/mp4",
                            ),
                            file = outsideFile,
                        ),
                    )
                },
            )

            val result = coordinator.extract(videoAsset())

            assertTrue(result.isFailure)
            assertEquals("TERMUX_EXTRACT_AUDIO_OUTPUT_PATH_INVALID", result.exceptionOrNull()?.message)
            assertTrue(outputRoot.listFiles().orEmpty().isEmpty())
            assertTrue(outsideFile.exists())
            assertEquals("outside", outsideFile.readText())
        } finally {
            outputRoot.deleteRecursively()
            outsideFile.delete()
        }
    }

    private fun coordinator(
        outputRoot: File,
        downloadFactory: suspend (String, File) -> Result<TermuxGeneratedMediaDownload>,
    ): TermuxMediaAudioExtractCoordinator {
        val client = FakeAudioJobClient(downloadFactory)
        return TermuxMediaAudioExtractCoordinator(
            client = client,
            sourceResolver = TermuxMediaSourceResolver { asset ->
                TermuxEngineMediaSource(asset.sizeBytes!!) {
                    ByteArrayInputStream(ByteArray(asset.sizeBytes.toInt()))
                }
            },
            outputDirectory = outputRoot,
            pollDelay = {},
        )
    }

    private fun videoAsset() = Asset(
        id = "asset-video",
        type = AssetType.VIDEO,
        uri = "content://com.localcut.editor.remote-media/remote-media/video.mp4",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 5L,
        displayName = "video.mp4",
        mimeType = "video/mp4",
        fingerprint = "fp-video",
    )

    private class FakeAudioJobClient(
        private val downloadFactory: suspend (String, File) -> Result<TermuxGeneratedMediaDownload>,
    ) : TermuxEngineJobClient {
        private val jobId = "123e4567-e89b-12d3-a456-426614174000"

        override suspend fun pair(pairingCode: String) = error("unused")
        override suspend fun ping() = error("unused")
        override suspend fun getStatus() = error("unused")
        override suspend fun getCapabilities() = error("unused")

        override suspend fun createAnalyzeMediaJob(
            input: TermuxAnalyzeMediaInput,
            idempotencyKey: String,
        ): Result<TermuxEngineJob> = Result.success(
            TermuxEngineJob(
                id = jobId,
                type = "ANALYZE_MEDIA",
                status = TermuxEngineJobStatus.QUEUED,
                progress = 0,
            ),
        )

        override suspend fun getJob(jobId: String) = error("unused")
        override suspend fun cancelJob(jobId: String) = error("unused")

        override suspend fun stageMedia(
            jobId: String,
            source: TermuxEngineMediaSource,
        ): Result<TermuxEngineStageResult> = Result.success(
            TermuxEngineStageResult(
                job = succeededJob(jobId),
                sizeBytes = source.contentLength,
                sha256 = "0".repeat(64),
            ),
        )

        override suspend fun downloadGeneratedMedia(
            jobId: String,
            destination: File,
        ): Result<TermuxGeneratedMediaDownload> = downloadFactory(jobId, destination)
    }

    companion object {
        private fun succeededJob(jobId: String) = TermuxEngineJob(
            id = jobId,
            type = "ANALYZE_MEDIA",
            status = TermuxEngineJobStatus.SUCCEEDED,
            progress = 100,
        )
    }
}
