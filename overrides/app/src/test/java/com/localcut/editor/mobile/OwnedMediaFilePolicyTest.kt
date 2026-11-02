package com.localcut.editor.mobile

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedMediaFilePolicyTest {
    @Test
    fun acceptsNonEmptyFileInsideRemoteMediaDirectory() {
        val filesDir = Files.createTempDirectory("localcut-files").toFile()
        val remoteMedia = filesDir.resolve("remote-media").apply { mkdirs() }
        val media = remoteMedia.resolve("reverse.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val accepted = OwnedMediaFilePolicy.requireOwned(filesDir, media)

        assertEquals(media.canonicalFile, accepted)
    }

    @Test
    fun rejectsFilesOutsideRemoteMediaAndEmptyFiles() {
        val filesDir = Files.createTempDirectory("localcut-files").toFile()
        val remoteMedia = filesDir.resolve("remote-media").apply { mkdirs() }
        val outside = filesDir.resolve("outside.mp4").apply { writeBytes(byteArrayOf(1)) }
        val empty = remoteMedia.resolve("empty.mp4").apply { createNewFile() }

        assertTrue(runCatching { OwnedMediaFilePolicy.requireOwned(filesDir, outside) }.isFailure)
        assertTrue(runCatching { OwnedMediaFilePolicy.requireOwned(filesDir, empty) }.isFailure)
    }
}
