package com.localcut.editor.mobile

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Stages only verified bytes into a caller-owned, new private directory. */
internal object PortableStudioStager {
    private const val RESERVE_BYTES = 16L * 1024 * 1024
    data class Staged(val recipe: String, val images: List<File>, val audio: File?, val audioMime: String?)

    fun stage(
        directory: File,
        openSource: () -> InputStream,
        checkCancelled: () -> Unit = {},
        availableBytes: (File) -> Long = { it.usableSpace },
    ): Staged {
        require(!directory.exists() && directory.mkdirs()) { "PACKAGE_STAGE_DIRECTORY" }
        var complete = false
        try {
            fun space(bytes: Long) {
                require(availableBytes(directory) >= RESERVE_BYTES + bytes) { "PACKAGE_STORAGE_INSUFFICIENT" }
            }
            val archive = File(directory, "archive.localcut")
            space(0)
            openSource().use { input ->
                archive.outputStream().use { output ->
                    copy(input, PortableStudioPackage.MAX_ARCHIVE_BYTES, checkCancelled, ::space) { buffer, count ->
                        output.write(buffer, 0, count)
                    }
                }
            }
            val verified = PortableStudioPackage.verify(archive, checkCancelled)
            val assets = verified.images + listOfNotNull(verified.audio)
            space(assets.sumOf { it.size })
            val files = linkedMapOf<String, File>()
            ZipFile(archive).use { zip ->
                for (asset in assets) {
                    checkCancelled()
                    val output = File(directory, asset.id + "." + extension(asset.mime))
                    require(output.createNewFile()) { "PACKAGE_STAGE_COLLISION" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val entry = zip.getEntry(asset.path) ?: error("PACKAGE_ASSET_MISSING")
                    val copied = zip.getInputStream(entry).use { input ->
                        output.outputStream().use { out ->
                            copy(input, asset.size, checkCancelled, ::space) { buffer, count ->
                                digest.update(buffer, 0, count)
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                    require(copied == asset.size && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == asset.sha256) {
                        "PACKAGE_ASSET_INTEGRITY"
                    }
                    validateMedia(output, asset.mime)
                    files[asset.id] = output
                }
            }
            check(archive.delete()) { "PACKAGE_ARCHIVE_CLEANUP" }
            checkCancelled()
            val result = Staged(verified.recipe, verified.images.map { files.getValue(it.id) },
                verified.audio?.let { files.getValue(it.id) }, verified.audio?.mime)
            complete = true
            return result
        } finally {
            if (!complete) directory.deleteRecursively()
        }
    }

    private fun copy(input: InputStream, limit: Long, checkCancelled: () -> Unit, space: (Long) -> Unit,
                     consume: (ByteArray, Int) -> Unit): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            require(count > 0 && total <= limit - count) { "PACKAGE_COPY_LIMIT" }
            space(count.toLong())
            consume(buffer, count)
            total += count
        }
        return total
    }

    private fun extension(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "audio/mpeg" -> "mp3"
        "audio/wav" -> "wav"
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/flac" -> "flac"
        else -> error("PACKAGE_MEDIA_UNSUPPORTED")
    }

    // Metadata preflight only. Full decoder/export errors still belong to the existing engine.
    private fun validateMedia(file: File, mime: String) {
        if (mime.startsWith("image/")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            require(options.outWidth > 0 && options.outHeight > 0 && options.outMimeType == mime) { "PACKAGE_IMAGE_INVALID" }
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" &&
                    (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0) {
                    "PACKAGE_AUDIO_INVALID"
                }
            } finally { retriever.release() }
        }
    }
}
