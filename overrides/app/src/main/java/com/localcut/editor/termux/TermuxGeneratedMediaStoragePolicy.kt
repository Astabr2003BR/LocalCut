package com.localcut.editor.termux

import java.io.File

internal object TermuxGeneratedMediaStoragePolicy {
    private const val DIRECTORY_NAME = "remote-media"

    fun durableDirectory(filesDir: File): File {
        val root = filesDir.canonicalFile
        if (!root.exists()) {
            require(root.mkdirs()) { "TERMUX_GENERATED_MEDIA_FILES_DIR_CREATE_FAILED" }
        }
        require(root.isDirectory) { "TERMUX_GENERATED_MEDIA_FILES_DIR_INVALID" }

        val directory = File(root, DIRECTORY_NAME).canonicalFile
        require(directory.parentFile == root) { "TERMUX_GENERATED_MEDIA_DIRECTORY_ESCAPE" }
        if (!directory.exists()) {
            require(directory.mkdirs()) { "TERMUX_GENERATED_MEDIA_DIRECTORY_CREATE_FAILED" }
        }
        require(directory.isDirectory) { "TERMUX_GENERATED_MEDIA_DIRECTORY_INVALID" }
        return directory
    }
}
