package com.localcut.editor.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import androidx.core.content.FileProvider
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class PortableStudioPackageSmokeTest {
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGPgEpEDAABoAD1UCKP3AAAAAElFTkSuQmCC")
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun asset(id: String = "frame", bytes: Long = png.size.toLong(), sha: String = digest(png)) =
        JSONObject().put("id", id).put("mime", "image/png").put("bytes", bytes).put("sha256", sha)
    private fun manifest(images: JSONArray = JSONArray().put(asset()), version: Int = 1): String =
        JSONObject().put("version", version).put("recipe", AutomaticVideoRecipe.EXAMPLE).put("images", images).toString()

    private fun withArchive(text: String, files: List<Pair<String, ByteArray>>, block: (File) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("portable-test-", ".localcut", cache)
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                (listOf("manifest.json" to text.toByteArray(Charsets.UTF_8)) + files).forEach { (name, data) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
            block(file)
        } finally { file.delete() }
    }

    @Test fun verifiesOrderedImagesAndRecipeWithoutExtractingFiles() {
        val images = JSONArray().put(asset("second")).put(asset("first"))
        withArchive(manifest(images), listOf("media/first" to png, "media/second" to png)) { file ->
            val result = PortableStudioPackage.verify(file)
            assertEquals(listOf("second", "first"), result.images.map { it.id })
            assertEquals(5_000_000L, result.plan.durationUs)
            assertEquals(0, result.plan.scenes.single().imageIndex)
            assertEquals(null, result.audio)
        }
    }

    @Test fun rejectsMissingUnexpectedTraversalAndHashMismatch() {
        val cases = listOf(
            manifest() to emptyList(),
            manifest() to listOf("media/frame" to png, "extra.txt" to png),
            manifest() to listOf("../frame" to png),
            manifest() to listOf("media/frame" to png.copyOf().apply { this[lastIndex] = 1 }),
            manifest(JSONArray().put(asset(bytes = png.size.toLong() + 1))) to listOf("media/frame" to png),
            manifest(JSONArray().put(asset()).put(asset())) to listOf("media/frame" to png),
            manifest(version = 2) to listOf("media/frame" to png),
            manifest().replace("\"version\":1", "\"version\":1,\"version\":1") to listOf("media/frame" to png),
            manifest().dropLast(1) + ",\"url\":\"https://example.com\"}" to listOf("media/frame" to png),
            manifest(JSONArray().put(asset(bytes = PortableStudioPackage.MAX_ASSET_BYTES + 1))) to listOf("media/frame" to png)
        )
        cases.forEach { (json, files) ->
            withArchive(json, files) { file ->
                assertTrue(runCatching { PortableStudioPackage.verify(file) }.isFailure)
            }
        }
    }

    @Test fun stagesPrivateImagesAndReadsThroughAndroidFileProvider() {
        withArchive(manifest(), listOf("media/frame" to png)) { archive ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dir = File(File(context.cacheDir, "portable-studio"), UUID.randomUUID().toString())
            try {
                val staged = PortableStudioStager.stage(dir, { archive.inputStream() })
                assertEquals(AutomaticVideoRecipe.EXAMPLE, staged.recipe)
                assertEquals("frame.png", staged.images.single().name)
                assertTrue(!File(dir, "archive.localcut").exists())
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.remote-media", staged.images.single())
                assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.contentEquals(png))
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun stagingFailureLeavesNoPartialDirectory() {
        withArchive(manifest(), listOf("media/frame" to png)) { archive ->
            val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            for (mode in listOf("space", "cancel", "corrupt")) {
                val dir = File(cache, "portable-test-${UUID.randomUUID()}")
                val outcome = runCatching {
                    PortableStudioStager.stage(dir,
                        openSource = { if (mode == "corrupt") byteArrayOf(1, 2, 3).inputStream() else archive.inputStream() },
                        checkCancelled = { if (mode == "cancel") error("cancelled") },
                        availableBytes = { if (mode == "space") 0L else Long.MAX_VALUE })
                }
                assertTrue(outcome.isFailure)
                assertTrue(!dir.exists())
            }
        }
    }

    @Test fun rejectsTruncatedZipAndHonorsCancellation() {
        withArchive(manifest(), listOf("media/frame" to png)) { file ->
            assertTrue(runCatching { PortableStudioPackage.verify(file) { error("cancelled") } }.isFailure)
            val bytes = file.readBytes()
            file.writeBytes(bytes.copyOf(bytes.size - 4))
            assertTrue(runCatching { PortableStudioPackage.verify(file) }.isFailure)
        }
    }
}
