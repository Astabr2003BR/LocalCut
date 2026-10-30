package com.localcut.editor.termux

import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedMediaGarbageCollectorTest {
    private val authority = "com.localcut.editor.remote-media"
    private val oldJob = "123e4567-e89b-12d3-a456-426614174000"
    private val oldOutput = "223e4567-e89b-12d3-a456-426614174001"
    private val oldName = "reverse-$oldJob-$oldOutput.mp4"
    private val now = 10L * GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS

    @Test
    fun activeUndoOrRedoBlocksScanAndDeletion() {
        val root = createTempDirectory("localcut-gc-history").toFile()
        try {
            val orphan = oldFile(root, oldName)
            val collector = collector(root)

            val undoResult = collector.collect(emptyList(), canUndo = true, canRedo = false).getOrThrow()
            assertTrue(undoResult.blockedByHistory)
            assertEquals(0, undoResult.scannedEntries)
            assertTrue(orphan.exists())

            val redoResult = collector.collect(emptyList(), canUndo = false, canRedo = true).getOrThrow()
            assertTrue(redoResult.blockedByHistory)
            assertEquals(0, redoResult.scannedEntries)
            assertTrue(orphan.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletesOnlyOldUnreferencedReverseAtTopLevel() {
        val root = createTempDirectory("localcut-gc-delete").toFile()
        try {
            val orphan = oldFile(root, oldName)
            val referencedName = "reverse-323e4567-e89b-12d3-a456-426614174002-423e4567-e89b-12d3-a456-426614174003.mp4"
            val referenced = oldFile(root, referencedName)
            val recentName = "reverse-523e4567-e89b-12d3-a456-426614174004-623e4567-e89b-12d3-a456-426614174005.mp4"
            val recent = File(root, recentName).apply {
                writeText("recent")
                setLastModified(now - GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS)
            }
            val shared = oldFile(root, "shared-723e4567-e89b-12d3-a456-426614174006.mp4")
            val subdir = File(root, "nested").apply { mkdirs() }
            val nested = oldFile(subdir, "reverse-823e4567-e89b-12d3-a456-426614174007-923e4567-e89b-12d3-a456-426614174008.mp4")

            val result = collector(root).collect(
                assetUris = listOf("content://$authority/remote-media/$referencedName"),
                canUndo = false,
                canRedo = false,
            ).getOrThrow()

            assertFalse(orphan.exists())
            assertTrue(referenced.exists())
            assertTrue(recent.exists())
            assertTrue(shared.exists())
            assertTrue(nested.exists())
            assertEquals(1, result.deletedFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun symlinkDirectoryAndEmptyGeneratedEntryFailClosed() {
        val root = createTempDirectory("localcut-gc-unsafe").toFile()
        val outside = createTempDirectory("localcut-gc-outside").toFile()
        try {
            val emptyName = "reverse-a23e4567-e89b-12d3-a456-426614174009-b23e4567-e89b-12d3-a456-426614174010.mp4"
            val empty = File(root, emptyName).apply {
                createNewFile()
                setLastModified(1L)
            }
            val directoryName = "reverse-c23e4567-e89b-12d3-a456-426614174011-d23e4567-e89b-12d3-a456-426614174012.mp4"
            val directory = File(root, directoryName).apply { mkdirs() }
            val target = oldFile(outside, "target.mp4")
            val linkName = "reverse-e23e4567-e89b-12d3-a456-426614174013-f23e4567-e89b-12d3-a456-426614174014.mp4"
            val link = File(root, linkName)
            Files.createSymbolicLink(link.toPath(), target.toPath())

            val result = collector(root).collect(emptyList(), canUndo = false, canRedo = false).getOrThrow()

            assertTrue(empty.exists())
            assertTrue(directory.exists())
            assertTrue(Files.isSymbolicLink(link.toPath()))
            assertTrue(target.exists())
            assertEquals(0, result.deletedFiles)
            assertTrue(result.skippedUnsafeEntries >= 3)
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun failedDeletionIsReportedWithoutTouchingOtherProtectedFiles() {
        val root = createTempDirectory("localcut-gc-failure").toFile()
        try {
            val orphan = oldFile(root, oldName)
            val shared = oldFile(root, "shared-123e4567-e89b-12d3-a456-426614174099.mp4")
            val collector = GeneratedMediaGarbageCollector(
                remoteMediaDirectory = root,
                providerAuthority = authority,
                gracePeriodMs = GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS,
                nowMs = { now },
                deleteFile = { false },
            )

            val result = collector.collect(emptyList(), canUndo = false, canRedo = false).getOrThrow()

            assertTrue(orphan.exists())
            assertTrue(shared.exists())
            assertEquals(0, result.deletedFiles)
            assertEquals(1, result.failedDeletions)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun collector(root: File) = GeneratedMediaGarbageCollector(
        remoteMediaDirectory = root,
        providerAuthority = authority,
        gracePeriodMs = GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS,
        nowMs = { now },
    )

    private fun oldFile(parent: File, name: String): File = File(parent, name).apply {
        writeText("payload")
        setLastModified(1L)
    }
}
