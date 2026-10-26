package com.localcut.editor.termux

import com.localcut.editor.domain.model.Asset
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
        val created = client.createAnalyzeMediaJob(input, idempotencyKey(asset, sizeBytes)).getOrThrow()
        when (created.status) {
            TermuxEngineJobStatus.QUEUED -> {
                val source = sourceResolver.resolve(asset)
                require(source.contentLength == sizeBytes) { "TERMUX_MEDIA_SIZE_MISMATCH" }
                client.stageMedia(created.id, source).getOrThrow().job
            }
            else -> created
        }
    }

    private fun idempotencyKey(asset: Asset, sizeBytes: Long): String {
        val material = buildString {
            append(asset.id); append('\n')
            append(asset.fingerprint); append('\n')
            append(sizeBytes); append('\n')
            append(asset.sourceDurationUs ?: -1L)
        }.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
            .joinToString("") { "%02x".format(it) }
        return "analyze-media-v1:$digest"
    }
}
