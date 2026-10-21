package com.localcut.editor.mobile

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RemoteMediaImportExecutor(
    private val context: Context,
    private val mediaIngestCoordinator: MediaIngestCoordinator,
    private val downloader: suspend (String) -> Result<DownloadedRemoteMedia> = { rawUrl ->
        RemoteMediaDownloader(context.cacheDir).download(rawUrl)
    },
    private val validator: suspend (File, String?) -> Result<RemoteMediaKind> = { file, declaredContentType ->
        withContext(Dispatchers.IO) {
            RemoteMediaValidator.validate(file, declaredContentType)
        }
    },
) {
    suspend fun importUrl(rawUrl: String): Result<MediaIngestResult> {
        var downloadedFile: File? = null
        var promotedFile: File? = null

        return try {
            val downloaded = downloader(rawUrl).getOrThrow()
            downloadedFile = downloaded.file
            val kind = validator(downloaded.file, downloaded.declaredContentType).getOrThrow()
            val promoted = withContext(Dispatchers.IO) {
                promoteForIngest(downloaded, kind)
            }
            promotedFile = promoted

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.remote-media",
                promoted,
            )
            val ingest = mediaIngestCoordinator.importUris(listOf(uri))
            require(ingest.importedCount == 1 && ingest.failedCount == 0) {
                ingest.errors.firstOrNull() ?: "REMOTE_MEDIA_IMPORT_FAILED"
            }
            Result.success(ingest)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                promotedFile?.delete()
                downloadedFile?.delete()
                cleanupRemoteDirectory()
            }
        }
    }

    private fun promoteForIngest(
        downloaded: DownloadedRemoteMedia,
        kind: RemoteMediaKind,
    ): File {
        val source = downloaded.file
        require(source.isFile && source.length() > 0L) { "REMOTE_MEDIA_FILE_INVALID" }
        val extension = extensionFor(downloaded.declaredContentType, kind)
        val stem = source.name.removeSuffix(".part").ifBlank { "remote-${UUID.randomUUID()}" }
        val target = File(source.parentFile ?: File(context.cacheDir, "remote-import"), "$stem$extension")
        if (target.absolutePath == source.absolutePath) return source
        if (target.exists()) target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        require(target.isFile && target.length() > 0L) { "REMOTE_MEDIA_PROMOTE_FAILED" }
        return target
    }

    private fun extensionFor(contentType: String?, kind: RemoteMediaKind): String {
        val normalized = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        return when (normalized) {
            "image/png" -> ".png"
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "audio/wav", "audio/x-wav", "audio/wave" -> ".wav"
            "audio/mpeg" -> ".mp3"
            "audio/mp4", "audio/x-m4a" -> ".m4a"
            "audio/ogg" -> ".ogg"
            "video/mp4" -> ".mp4"
            "video/webm" -> ".webm"
            "video/quicktime" -> ".mov"
            else -> when (kind) {
                RemoteMediaKind.IMAGE -> ".png"
                RemoteMediaKind.AUDIO -> ".wav"
                RemoteMediaKind.VIDEO -> ".mp4"
            }
        }
    }

    private fun cleanupRemoteDirectory() {
        val directory = File(context.cacheDir, "remote-import")
        if (directory.isDirectory && directory.listFiles().orEmpty().isEmpty()) {
            directory.delete()
        }
    }
}
