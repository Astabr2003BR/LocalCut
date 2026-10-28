package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

fun interface TermuxMediaSourceResolver {
    fun resolve(asset: Asset): TermuxEngineMediaSource
}

data class TermuxJobAwaitPolicy(
    val maxPolls: Int = 900,
    val initialDelayMs: Long = 250L,
    val maxDelayMs: Long = 1_000L,
) {
    init {
        require(maxPolls in 1..2_000) { "TERMUX_JOB_AWAIT_POLLS_INVALID" }
        require(initialDelayMs in 1L..10_000L) { "TERMUX_JOB_AWAIT_INITIAL_DELAY_INVALID" }
        require(maxDelayMs in initialDelayMs..10_000L) { "TERMUX_JOB_AWAIT_MAX_DELAY_INVALID" }
    }
}

class TermuxMediaAnalyzeCoordinator(
    private val client: TermuxEngineJobClient,
    private val sourceResolver: TermuxMediaSourceResolver,
) {
    suspend fun analyze(asset: Asset): Result<TermuxEngineJob> = runCatching {
        val sizeBytes = asset.sizeBytes?.takeIf { it > 0L }
            ?: error("TERMUX_MEDIA_SIZE_UNAVAILABLE")
        val input = TermuxAnalyzeMediaInput(
            assetId = asset.id,
            displayName = asset.displayName,
            mimeType = asset.mimeType ?: "application/octet-stream",
            sizeBytes = sizeBytes,
            sourceDurationUs = asset.sourceDurationUs,
        )
        val created = client.createAnalyzeMediaJob(input, idempotencyKey("analyze-media-v1", asset, sizeBytes, null)).getOrThrow()
        when (created.status) {
            TermuxEngineJobStatus.QUEUED -> {
                val source = sourceResolver.resolve(asset)
                require(source.contentLength == sizeBytes) { "TERMUX_MEDIA_SIZE_MISMATCH" }
                client.stageMedia(created.id, source).getOrThrow().job
            }
            else -> created
        }
    }
}

class TermuxMediaTrackingCoordinator(
    private val client: TermuxEngineJobClient,
    private val sourceResolver: TermuxMediaSourceResolver,
) {
    suspend fun analyze(
        asset: Asset,
        options: TermuxTrackingOptions = TermuxTrackingOptions(),
    ): Result<TermuxGlobalMotionAnalysis> = runCatching {
        require(asset.type == AssetType.VIDEO && asset.mimeType?.startsWith("video/") == true) {
            "TERMUX_TRACKING_REQUIRES_VIDEO"
        }
        val sizeBytes = asset.sizeBytes?.takeIf { it > 0L }
            ?: error("TERMUX_MEDIA_SIZE_UNAVAILABLE")
        val input = TermuxAnalyzeMediaInput(
            assetId = asset.id,
            displayName = asset.displayName,
            mimeType = requireNotNull(asset.mimeType),
            sizeBytes = sizeBytes,
            sourceDurationUs = asset.sourceDurationUs,
            mode = TermuxAnalyzeMediaMode.TRACKING,
            trackingOptions = options,
        )
        val created = client.createAnalyzeMediaJob(
            input,
            idempotencyKey("analyze-tracking-v1", asset, sizeBytes, options),
        ).getOrThrow()
        val finished = when (created.status) {
            TermuxEngineJobStatus.QUEUED -> {
                val source = sourceResolver.resolve(asset)
                require(source.contentLength == sizeBytes) { "TERMUX_MEDIA_SIZE_MISMATCH" }
                client.stageMedia(created.id, source).getOrThrow().job
            }
            else -> created
        }
        require(finished.status == TermuxEngineJobStatus.SUCCEEDED) {
            finished.errorCode ?: "TERMUX_TRACKING_JOB_NOT_SUCCEEDED"
        }
        require(finished.analysisMode == "opencv-global-motion-v1") {
            "TERMUX_TRACKING_ANALYSIS_MODE_INVALID"
        }
        finished.globalMotion ?: error("TERMUX_TRACKING_RESULT_MISSING")
    }
}

class TermuxMediaReverseCoordinator(
    private val client: TermuxEngineJobClient,
    private val sourceResolver: TermuxMediaSourceResolver,
    private val outputDirectory: File,
    private val awaitPolicy: TermuxJobAwaitPolicy = TermuxJobAwaitPolicy(),
    private val pollDelay: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun reverse(
        asset: Asset,
        options: TermuxReverseOptions = TermuxReverseOptions(),
    ): Result<TermuxGeneratedMediaDownload> = try {
        Result.success(reverseOrThrow(asset, options))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun reverseOrThrow(
        asset: Asset,
        options: TermuxReverseOptions,
    ): TermuxGeneratedMediaDownload {
        require(asset.type == AssetType.VIDEO && asset.mimeType?.startsWith("video/") == true) {
            "TERMUX_REVERSE_REQUIRES_VIDEO"
        }
        val sizeBytes = asset.sizeBytes?.takeIf { it > 0L }
            ?: error("TERMUX_MEDIA_SIZE_UNAVAILABLE")
        val durationUs = asset.sourceDurationUs?.takeIf { it > 0L }
            ?: error("TERMUX_REVERSE_DURATION_UNAVAILABLE")
        val input = TermuxAnalyzeMediaInput(
            assetId = asset.id,
            displayName = asset.displayName,
            mimeType = requireNotNull(asset.mimeType),
            sizeBytes = sizeBytes,
            sourceDurationUs = durationUs,
            mode = TermuxAnalyzeMediaMode.REVERSE,
            reverseOptions = options,
        )
        val created = client.createAnalyzeMediaJob(
            input,
            idempotencyKey("reverse-media-v1", asset, sizeBytes, null, options),
        ).getOrThrow()
        val afterStage = when (created.status) {
            TermuxEngineJobStatus.QUEUED -> {
                val source = sourceResolver.resolve(asset)
                require(source.contentLength == sizeBytes) { "TERMUX_MEDIA_SIZE_MISMATCH" }
                client.stageMedia(created.id, source).getOrThrow().job
            }
            else -> created
        }
        val finished = awaitTerminalJob(afterStage)
        require(finished.status == TermuxEngineJobStatus.SUCCEEDED) {
            finished.errorCode ?: "TERMUX_REVERSE_JOB_NOT_SUCCEEDED"
        }

        val root = outputDirectory.canonicalFile
        if (!root.exists()) require(root.mkdirs()) { "TERMUX_REVERSE_OUTPUT_DIRECTORY_CREATE_FAILED" }
        require(root.isDirectory) { "TERMUX_REVERSE_OUTPUT_DIRECTORY_INVALID" }
        val destination = File(root, "reverse-${finished.id}-${UUID.randomUUID()}.mp4")
        val download = client.downloadGeneratedMedia(finished.id, destination).getOrThrow()
        require(download.job.id == finished.id) { "TERMUX_REVERSE_DOWNLOAD_JOB_MISMATCH" }
        require(download.file.canonicalFile.parentFile == root) { "TERMUX_REVERSE_OUTPUT_PATH_INVALID" }
        return download
    }

    private suspend fun awaitTerminalJob(initial: TermuxEngineJob): TermuxEngineJob {
        var current = initial
        if (current.status.isTerminal()) return current
        var delayMs = awaitPolicy.initialDelayMs
        repeat(awaitPolicy.maxPolls) {
            pollDelay(delayMs)
            current = client.getJob(current.id).getOrThrow()
            if (current.status.isTerminal()) return current
            delayMs = minOf(awaitPolicy.maxDelayMs, delayMs * 2L)
        }
        error("TERMUX_JOB_WAIT_TIMEOUT")
    }
}

private fun TermuxEngineJobStatus.isTerminal(): Boolean = when (this) {
    TermuxEngineJobStatus.SUCCEEDED,
    TermuxEngineJobStatus.FAILED,
    TermuxEngineJobStatus.CANCELLED -> true
    else -> false
}

private fun idempotencyKey(
    prefix: String,
    asset: Asset,
    sizeBytes: Long,
    tracking: TermuxTrackingOptions?,
    reverse: TermuxReverseOptions? = null,
): String {
    val material = buildString {
        append(asset.id); append('\n')
        append(asset.fingerprint); append('\n')
        append(sizeBytes); append('\n')
        append(asset.sourceDurationUs ?: -1L)
        tracking?.let {
            append('\n'); append(it.maxFrames)
            append('\n'); append(it.sampleEvery)
            append('\n'); append(it.maxCorners)
        }
        reverse?.let {
            append('\n'); append(it.chunkDurationUs)
        }
    }.toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(material)
        .joinToString("") { "%02x".format(it) }
    return "$prefix:$digest"
}
