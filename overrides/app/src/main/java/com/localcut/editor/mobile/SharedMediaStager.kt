package com.localcut.editor.mobile

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StagedSharedMedia(
    val uri: Uri,
    val backingFile: File,
)

class SharedMediaStager(
    private val context: Context,
) {
    suspend fun stage(source: Uri): Result<StagedSharedMedia> = withContext(Dispatchers.IO) {
        var partial: File? = null
        var target: File? = null
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(source)
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?: throw IllegalArgumentException("SHARED_MEDIA_TYPE_UNAVAILABLE")
            val extension = extensionFor(mimeType)
            val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
            require(directory.isDirectory) { "SHARED_MEDIA_STORAGE_UNAVAILABLE" }

            val stem = "shared-${UUID.randomUUID()}"
            partial = File(directory, "$stem.part")
            target = File(directory, "$stem.$extension")

            resolver.openInputStream(source)?.use { input ->
                partial.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("SHARED_MEDIA_SOURCE_UNAVAILABLE")

            require(partial.isFile && partial.length() > 0L) { "SHARED_MEDIA_EMPTY" }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            require(target.isFile && target.length() > 0L) { "SHARED_MEDIA_STAGE_FAILED" }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.remote-media",
                target,
            )
            Result.success(StagedSharedMedia(uri = uri, backingFile = target))
        } catch (error: CancellationException) {
            target?.delete()
            throw error
        } catch (error: Throwable) {
            target?.delete()
            Result.failure(error)
        } finally {
            partial?.delete()
        }
    }

    private fun extensionFor(mimeType: String): String {
        val explicit = when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/ogg" -> "ogg"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            else -> null
        }
        val extension = explicit ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        require(!extension.isNullOrBlank()) { "SHARED_MEDIA_TYPE_UNSUPPORTED" }
        return extension.lowercase()
    }
}
