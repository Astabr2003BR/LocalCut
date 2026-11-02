package com.localcut.editor.projectpackage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPackageArchiveTest {
    @Test
    fun archiveRoundTripPreservesProjectAndAssets() {
        val manifest = ProjectPackageManifest(
            assets = listOf(ProjectPackageAsset("a", "assets/a/file.bin", "fp", 3L)),
        )
        val output = ByteArrayOutputStream()

        ProjectPackageArchive.write(
            output = output,
            manifest = manifest,
            projectJson = "{\"id\":\"p\"}",
            assetSource = { id -> if (id == "a") ByteArrayInputStream(byteArrayOf(1, 2, 3)) else null },
        )

        val read = ProjectPackageArchive.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals("{\"id\":\"p\"}", read.projectJson)
        assertArrayEquals(byteArrayOf(1, 2, 3), read.assets.getValue("a"))
        assertTrue(read.validation.issues.isEmpty())
        assertTrue(read.validation.missingEntries.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun writerRejectsMissingAssetBytes() {
        ProjectPackageArchive.write(
            output = ByteArrayOutputStream(),
            manifest = ProjectPackageManifest(
                assets = listOf(ProjectPackageAsset("missing", "assets/missing/file.bin", "fp", 1L)),
            ),
            projectJson = "{}",
            assetSource = { null },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun readerRejectsUnsafeZipEntryEvenIfManifestIsSafe() {
        val raw = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(raw).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../evil"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        ProjectPackageArchive.read(ByteArrayInputStream(raw.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun readerRejectsArchivePastConfiguredTotalLimit() {
        val raw = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(raw).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("project.json"))
            zip.write(ByteArray(32) { 1 })
            zip.closeEntry()
        }
        ProjectPackageArchive.read(
            input = ByteArrayInputStream(raw.toByteArray()),
            limits = ProjectPackageLimits(maxEntryBytes = 64, maxTotalBytes = 16, maxEntries = 4),
        )
    }
}
