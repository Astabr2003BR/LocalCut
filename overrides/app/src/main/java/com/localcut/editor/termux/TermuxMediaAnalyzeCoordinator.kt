package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.security.MessageDigest

fun interface TermuxMediaSourceResolver {
    fun resolve(asset: Asset): TermuxEngineMediaSource
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

private fun idempotencyKey(
    prefix: String,
    asset: Asset,
    sizeBytes: Long,
    tracking: TermuxTrackingOptions?,
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
    }.toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(material)
        .joinToString("") { "%02x".format(it) }
    return "$prefix:$digest"
}
