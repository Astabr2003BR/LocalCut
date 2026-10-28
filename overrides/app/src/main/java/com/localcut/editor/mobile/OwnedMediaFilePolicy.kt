package com.localcut.editor.mobile

import java.io.File

internal object OwnedMediaFilePolicy {
    private const val DIRECTORY_NAME = "remote-media"

    fun requireOwned(filesDir: File, candidate: File): File {
        val root = filesDir.canonicalFile
        require(root.isDirectory || root.mkdirs()) { "OWNED_MEDIA_FILES_DIR_UNAVAILABLE" }

        val remoteMedia = File(root, DIRECTORY_NAME).canonicalFile
        require(remoteMedia.parentFile == root) { "OWNED_MEDIA_DIRECTORY_ESCAPE" }
        require(remoteMedia.isDirectory || remoteMedia.mkdirs()) { "OWNED_MEDIA_DIRECTORY_UNAVAILABLE" }

        val file = candidate.canonicalFile
        require(file.parentFile == remoteMedia) { "OWNED_MEDIA_FILE_ESCAPE" }
        require(file.isFile) { "OWNED_MEDIA_FILE_MISSING" }
        require(file.length() > 0L) { "OWNED_MEDIA_FILE_EMPTY" }
        return file
    }
}
