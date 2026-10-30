package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class TermuxMediaAudioExtractCoordinator(
    private val client: TermuxEngineJobClient,
    private val sourceResolver: TermuxMediaSourceResolver,
    private val outputDirectory: File,
    private val awaitPolicy: TermuxJobAwaitPolicy = TermuxJobAwaitPolicy(),
    private val pollDelay: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun extract(asset: Asset): Result<TermuxGeneratedMediaDownload> = try {
        Result.success(extractOrThrow(asset))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun extractOrThrow(asset: Asset): TermuxGeneratedMediaDownload {
        require(asset.type == AssetType.VIDEO && asset.mimeType?.startsWith("video/") == true) {
            "TERMUX_EXTRACT_AUDIO_REQUIRES_VIDEO"
        }
        val sizeBytes = asset.sizeBytes?.takeIf { it > 0L }
            ?: error("TERMUX_MEDIA_SIZE_UNAVAILABLE")
        val durationUs = asset.sourceDurationUs?.takeIf { it in 1L..600_000_000L }
            ?: error("TERMUX_EXTRACT_AUDIO_DURATION_UNAVAILABLE")
        val input = TermuxAnalyzeMediaInput(
            assetId = asset.id,
            displayName = asset.displayName,
            mimeType = requireNotNull(asset.mimeType),
            sizeBytes = sizeBytes,
            sourceDurationUs = durationUs,
            mode = TermuxAnalyzeMediaMode.EXTRACT_AUDIO,
        )
        val created = client.createAnalyzeMediaJob(
            input,
            audioIdempotencyKey(asset, sizeBytes),
        ).getOrThrow()
        val afterStage = when (created.status) {
            TermuxEngineJobStatus.QUEUED -> {
                val source = sourceResolver.resolve(asset)
                require(source.contentLength == sizeBytes) { "TERMUX_MEDIA_SIZE_MISMATCH" }
                client.stageMedia(created.id, source).getOrThrow().job
            }
            else -> created
        }
        val finished = awaitAudioExtractionJob(client, afterStage, awaitPolicy, pollDelay)
        require(finished.status == TermuxEngineJobStatus.SUCCEEDED) {
            finished.errorCode ?: "TERMUX_EXTRACT_AUDIO_JOB_NOT_SUCCEEDED"
        }

        val root = outputDirectory.canonicalFile
        if (!root.exists()) require(root.mkdirs()) { "TERMUX_EXTRACT_AUDIO_OUTPUT_DIRECTORY_CREATE_FAILED" }
        require(root.isDirectory) { "TERMUX_EXTRACT_AUDIO_OUTPUT_DIRECTORY_INVALID" }
        val destination = File(root, "audio-${finished.id}-${UUID.randomUUID()}.m4a").canonicalFile
        require(destination.parentFile == root) { "TERMUX_EXTRACT_AUDIO_OUTPUT_PATH_INVALID" }
        val download = client.downloadGeneratedMedia(finished.id, destination).getOrThrow()
        require(download.job.id == finished.id) { "TERMUX_EXTRACT_AUDIO_DOWNLOAD_JOB_MISMATCH" }
        require(download.media.mimeType == "audio/mp4") { "TERMUX_EXTRACT_AUDIO_DOWNLOAD_TYPE_INVALID" }
        require(download.file.canonicalFile == destination) { "TERMUX_EXTRACT_AUDIO_OUTPUT_PATH_INVALID" }
        return download
    }
}

private suspend fun awaitAudioExtractionJob(
    client: TermuxEngineJobClient,
    initial: TermuxEngineJob,
    policy: TermuxJobAwaitPolicy,
    pollDelay: suspend (Long) -> Unit,
): TermuxEngineJob {
    var current = initial
    if (current.status.isAudioExtractionTerminal()) return current
    var delayMs = policy.initialDelayMs
    repeat(policy.maxPolls) {
        pollDelay(delayMs)
        current = client.getJob(current.id).getOrThrow()
        if (current.status.isAudioExtractionTerminal()) return current
        delayMs = minOf(policy.maxDelayMs, delayMs * 2L)
    }
    error("TERMUX_JOB_WAIT_TIMEOUT")
}

private fun TermuxEngineJobStatus.isAudioExtractionTerminal(): Boolean = when (this) {
    TermuxEngineJobStatus.SUCCEEDED,
    TermuxEngineJobStatus.FAILED,
    TermuxEngineJobStatus.CANCELLED -> true
    else -> false
}

private fun audioIdempotencyKey(asset: Asset, sizeBytes: Long): String {
    val material = buildString {
        append(asset.id); append('\n')
        append(asset.fingerprint); append('\n')
        append(sizeBytes); append('\n')
        append(asset.sourceDurationUs ?: -1L)
    }.toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(material)
        .joinToString("") { "%02x".format(it) }
    return "extract-audio-v1:$digest"
}
