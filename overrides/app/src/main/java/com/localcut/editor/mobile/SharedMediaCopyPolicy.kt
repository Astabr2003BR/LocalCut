package com.localcut.editor.mobile

import java.io.InputStream
import java.io.OutputStream

/**
 * Enforces the same per-kind size ceilings used by remote imports, even when an
 * Android content provider omits its file size or returns an incorrect length.
 * The copy is streaming: memory usage stays constant for large media.
 */
object SharedMediaCopyPolicy {
    private const val BUFFER_SIZE = 32 * 1024

    fun limitForMime(mimeType: String): Long {
        val normalized = mimeType.substringBefore(';').trim().lowercase()
        val kind = when {
            normalized.startsWith("image/") -> RemoteMediaKind.IMAGE
            normalized.startsWith("audio/") -> RemoteMediaKind.AUDIO
            normalized.startsWith("video/") -> RemoteMediaKind.VIDEO
            else -> throw IllegalArgumentException("SHARED_MEDIA_TYPE_UNSUPPORTED")
        }
        return RemoteMediaValidator.limitBytes(kind)
    }

    fun copy(input: InputStream, output: OutputStream, maximumBytes: Long): Long {
        require(maximumBytes > 0L) { "SHARED_MEDIA_LIMIT_INVALID" }
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(count > 0) { "SHARED_MEDIA_READ_STALLED" }
            require(count.toLong() <= maximumBytes - copied) { "SHARED_MEDIA_TOO_LARGE" }
            output.write(buffer, 0, count)
            copied += count.toLong()
        }
        require(copied > 0L) { "SHARED_MEDIA_EMPTY" }
        return copied
    }
}
