package com.localcut.editor.termux

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxGeneratedMediaStoragePolicyTest {
    @Test
    fun reverseOutputUsesDurableRemoteMediaDirectoryUnderFilesDir() {
        val filesDir = Files.createTempDirectory("localcut-files").toFile()
        try {
            val directory = TermuxGeneratedMediaStoragePolicy.durableDirectory(filesDir)

            assertEquals(
                filesDir.resolve("remote-media").canonicalFile,
                directory.canonicalFile,
            )
            assertTrue(directory.isDirectory)
            assertFalse(directory.path.contains("cache"))
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
