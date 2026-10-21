package com.localcut.editor.mobile

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

enum class RemoteMediaKind {
    IMAGE,
    AUDIO,
    VIDEO,
}

object RemoteMediaValidator {
    private const val IMAGE_LIMIT_BYTES = 50L * 1024L * 1024L
    private const val AUDIO_LIMIT_BYTES = 200L * 1024L * 1024L
    private const val VIDEO_LIMIT_BYTES = 1024L * 1024L * 1024L

    fun validate(file: File, declaredContentType: String?): Result<RemoteMediaKind> = runCatching {
        require(file.isFile && file.length() > 0L) { "REMOTE_MEDIA_FILE_INVALID" }

        val detected = detectImage(file) ?: detectAv(file)
            ?: error("REMOTE_MEDIA_TYPE_UNRECOGNIZED")
        require(file.length() <= limitBytes(detected)) { "REMOTE_MEDIA_TOO_LARGE" }

        declaredKind(declaredContentType)?.let { declared ->
            require(declared == detected) { "REMOTE_MEDIA_MIME_MISMATCH" }
        }

        detected
    }

    fun limitBytes(kind: RemoteMediaKind): Long = when (kind) {
        RemoteMediaKind.IMAGE -> IMAGE_LIMIT_BYTES
        RemoteMediaKind.AUDIO -> AUDIO_LIMIT_BYTES
        RemoteMediaKind.VIDEO -> VIDEO_LIMIT_BYTES
    }

    private fun detectImage(file: File): RemoteMediaKind? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) RemoteMediaKind.IMAGE else null
    }

    private fun detectAv(file: File): RemoteMediaKind? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                ?.equals("yes", ignoreCase = true) == true
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.equals("yes", ignoreCase = true) == true
            when {
                hasVideo -> RemoteMediaKind.VIDEO
                hasAudio -> RemoteMediaKind.AUDIO
                else -> {
                    val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.lowercase()
                    when {
                        mime?.startsWith("video/") == true -> RemoteMediaKind.VIDEO
                        mime?.startsWith("audio/") == true -> RemoteMediaKind.AUDIO
                        else -> null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun declaredKind(contentType: String?): RemoteMediaKind? {
        val normalized = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: return null
        return when {
            normalized.startsWith("image/") -> RemoteMediaKind.IMAGE
            normalized.startsWith("audio/") -> RemoteMediaKind.AUDIO
            normalized.startsWith("video/") -> RemoteMediaKind.VIDEO
            else -> null
        }
    }
}
