package com.localcut.editor.mobile

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.media.ProjectCompositionCompiler
import com.localcut.editor.media.RenderMode
import com.localcut.editor.domain.model.TrackType
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
@OptIn(UnstableApi::class)
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

    @Test fun exportsPackagedImageAndAudioThroughSharedProjectBuilder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val image = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).let { bitmap ->
            try {
                bitmap.eraseColor(Color.GREEN)
                ByteArrayOutputStream().use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                    out.toByteArray()
                }
            } finally { bitmap.recycle() }
        }
        val samples = 44_100 * 2
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + samples * 2)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1.toShort()); putShort(1.toShort()); putInt(44_100); putInt(88_200)
            putShort(2.toShort()); putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII)); putInt(samples * 2)
            repeat(samples) { index -> putShort((kotlin.math.sin(index * 2.0 * kotlin.math.PI / 100.0) * 4000).toInt().toShort()) }
        }.array()
        val recipe = """{"version":1,"format":"16:9","scenes":[{"image":1,"durationMs":1000}]}"""
        val json = JSONObject().put("version", 1).put("recipe", recipe)
            .put("images", JSONArray().put(asset(bytes = image.size.toLong(), sha = digest(image))))
            .put("audio", JSONObject().put("id", "tone").put("mime", "audio/wav").put("bytes", wav.size).put("sha256", digest(wav)))
        withArchive(json.toString(), listOf("media/frame" to image, "media/tone" to wav)) { archive ->
            val stageDir = File(File(context.cacheDir, "portable-studio"), UUID.randomUUID().toString())
            val workDir = File(context.cacheDir, "automatic-smoke-${UUID.randomUUID()}")
            val output = File(context.cacheDir, "portable-export-${UUID.randomUUID()}.mp4")
            val running = AtomicReference<Transformer?>()
            try {
                val staged = PortableStudioStager.stage(stageDir, { archive.inputStream() })
                val authority = "${context.packageName}.remote-media"
                val selected = staged.images.map { FileProvider.getUriForFile(context, authority, it).toString() }
                val audio = FileProvider.getUriForFile(context, authority, requireNotNull(staged.audio)).toString()
                val plan = AutomaticVideoRecipe.parse(staged.recipe, selected.size)
                val project = AutomaticVideoProjectBuilder.build(context, plan, selected, audio, staged.audioMime, workDir)
                assertTrue(!File(workDir, "image-0.png.source").exists())
                val invalidImage = File(stageDir, "invalid.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
                val invalidImageUri = FileProvider.getUriForFile(context, authority, invalidImage).toString()
                val invalidImageWork = File(context.cacheDir, "invalid-image-${UUID.randomUUID()}")
                try {
                    assertTrue(runCatching {
                        AutomaticVideoProjectBuilder.build(context, plan, listOf(invalidImageUri), null, null, invalidImageWork)
                    }.isFailure)
                    assertTrue(!File(invalidImageWork, "image-0.png.source").exists())
                    assertTrue(!File(invalidImageWork, "image-0.png").exists())
                } finally { invalidImageWork.deleteRecursively() }
                val copiedAudio = project.assets.single { it.id == "audio" }
                assertEquals(digest(wav), digest(File(android.net.Uri.parse(copiedAudio.uri).path!!).readBytes()))
                assertEquals(2_000_000L, copiedAudio.sourceDurationUs)
                val invalidAudio = File(stageDir, "invalid.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
                val invalidUri = FileProvider.getUriForFile(context, authority, invalidAudio).toString()
                val invalidWork = File(context.cacheDir, "invalid-audio-${UUID.randomUUID()}")
                try {
                    assertTrue(runCatching {
                        AutomaticVideoProjectBuilder.build(context, plan, selected, invalidUri, "audio/wav", invalidWork)
                    }.isFailure)
                } finally { invalidWork.deleteRecursively() }
                assertEquals(1_000_000L, project.tracks.first { it.type == TrackType.VISUAL }.clips.single().durationUs)
                assertEquals(1_000_000L, project.tracks.first { it.type == TrackType.AUDIO }.clips.single().durationUs)
                val composition = ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)).compile(project, RenderMode.EXPORT).getOrThrow()
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Throwable?>()
                instrumentation.runOnMainSync {
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) { completed.countDown() }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                failure.set(exportException)
                                completed.countDown()
                            }
                        }).build()
                    running.set(transformer)
                    transformer.start(composition, output.absolutePath)
                }
                assertTrue("Portable export timed out", completed.await(120, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("Portable export failed", it) }
                assertTrue(output.isFile && output.length() > 0)
                val inspection = com.localcut.editor.bridge.agent.AndroidBridgeExportFileReviewer.inspectWithSamples(output, project)
                val report = JSONObject(inspection.reportJson)
                assertEquals(1920, report.getInt("width"))
                assertEquals(1080, report.getInt("height"))
                assertEquals(1, report.getInt("videoTracks"))
                assertEquals(1, report.getInt("audioTracks"))
                assertEquals(digest(output.readBytes()), report.getString("sha256"))
                assertEquals(output.length(), report.getLong("bytes"))
                assertTrue(report.getBoolean("durationMatches"))
                assertTrue(report.getBoolean("resolutionMatches"))
                assertTrue(!report.getBoolean("visualQualityVerified"))
                assertTrue(!report.getBoolean("audioSyncVerified"))
                assertTrue(!report.getBoolean("fullDecodeVerified"))
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    assertEquals("decoded", report.getString("sampleStatus"))
                    assertEquals(3, report.getInt("decodedFrames"))
                    assertEquals(0, report.getJSONArray("nearBlackSamplePositionsUs").length())
                    assertTrue(report.getBoolean("nearBlackHeuristicOnly"))
                    assertEquals(3, inspection.samples.size)
                    assertEquals(report.getString("sha256"), inspection.sha256)
                    inspection.samples.forEach { sample ->
                        assertTrue(sample.jpeg.size <= 65_536)
                        val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(sample.jpeg, 0, sample.jpeg.size))
                        try {
                            assertEquals(sample.width, bitmap.width)
                            assertEquals(sample.height, bitmap.height)
                            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                            assertTrue(Color.green(pixel) > Color.red(pixel) + 40 && Color.green(pixel) > Color.blue(pixel) + 40)
                        } finally { bitmap.recycle() }
                    }
                }
                val corrupt = File(workDir, "corrupt.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
                assertTrue(runCatching { com.localcut.editor.bridge.agent.AndroidBridgeExportFileReviewer.inspect(corrupt, project) }.isFailure)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(output.absolutePath)
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                    assertEquals(1920, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt())
                    assertEquals(1080, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt())
                    val duration = requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
                    assertTrue("Unexpected duration: $duration", duration in 900L..1150L)
                    val frame = requireNotNull(retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                    try {
                        val center = frame.getPixel(frame.width / 2, frame.height / 2)
                        assertTrue(Color.green(center) > Color.red(center) + 40 && Color.green(center) > Color.blue(center) + 40)
                    } finally { frame.recycle() }
                } finally { retriever.release() }
                // Reuse the actual rendered MP4 as an existing local video source.
                val videoFile = File(stageDir, "existing.mp4")
                output.copyTo(videoFile)
                val videoHash = digest(videoFile.readBytes())
                val videoUri = FileProvider.getUriForFile(context, authority, videoFile).toString()
                val videoDir = File(workDir, "video-project")
                val videoPlan = AutomaticVideoRecipe.parse("""{"version":1,"format":"16:9","scenes":[{"image":1,"sourceInMs":200,"durationMs":500}]}""", 1)
                val videoProject = AutomaticVideoProjectBuilder.build(context, videoPlan, listOf(videoUri), null, null, videoDir)
                assertEquals(com.localcut.editor.domain.model.AssetType.VIDEO, videoProject.assets.single().type)
                assertEquals(500_000L, videoProject.tracks.first { it.type == TrackType.VISUAL }.clips.single().durationUs)
                val clip = videoProject.tracks.first { it.type == TrackType.VISUAL }.clips.single()
                assertEquals(200_000L, clip.sourceInUs)
                assertEquals(700_000L, clip.sourceOutUs)
                val videoComposition = ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)).compile(videoProject, RenderMode.EXPORT).getOrThrow()
                val editedOutput = File(workDir, "edited.mp4")
                val editedDone = CountDownLatch(1)
                val editedFailure = AtomicReference<Throwable?>()
                instrumentation.runOnMainSync {
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) { editedDone.countDown() }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                editedFailure.set(exportException)
                                editedDone.countDown()
                            }
                        }).build()
                    running.set(transformer)
                    transformer.start(videoComposition, editedOutput.absolutePath)
                }
                assertTrue("Edited MP4 export timed out", editedDone.await(120, TimeUnit.SECONDS))
                editedFailure.get()?.let { throw AssertionError("Edited MP4 export failed", it) }
                assertTrue(editedOutput.isFile && editedOutput.length() > 0)
                val edited = MediaMetadataRetriever()
                try {
                    edited.setDataSource(editedOutput.absolutePath)
                    assertEquals("yes", edited.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                    val duration = requireNotNull(edited.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
                    assertTrue("Edited duration: $duration", duration in 400L..650L)
                    val frame = requireNotNull(edited.getFrameAtTime(200_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                    try {
                        val center = frame.getPixel(frame.width / 2, frame.height / 2)
                        assertTrue(Color.green(center) > Color.red(center) + 40 && Color.green(center) > Color.blue(center) + 40)
                    } finally { frame.recycle() }
                } finally { edited.release() }
                val invalidOffset = AutomaticVideoRecipe.parse("""{"version":1,"format":"16:9","scenes":[{"image":1,"sourceInMs":900,"durationMs":500}]}""", 1)
                assertTrue(runCatching { AutomaticVideoProjectBuilder.build(context, invalidOffset, listOf(videoUri), null, null, File(workDir, "invalid-offset")) }.isFailure)
                assertTrue(runCatching { AutomaticVideoProjectBuilder.build(context, videoPlan, selected, null, null, File(workDir, "image-offset")) }.isFailure)
                assertEquals(videoHash, digest(videoFile.readBytes()))
                val tooLong = AutomaticVideoRecipe.parse("""{"version":1,"format":"16:9","scenes":[{"image":1,"durationMs":5000}]}""", 1)
                assertTrue(runCatching { AutomaticVideoProjectBuilder.build(context, tooLong, listOf(videoUri), null, null, File(workDir, "too-long")) }.isFailure)
                assertEquals(digest(image), digest(staged.images.single().readBytes()))
                assertEquals(digest(wav), digest(requireNotNull(staged.audio).readBytes()))
            } finally {
                running.get()?.let { transformer -> instrumentation.runOnMainSync { transformer.cancel() } }
                stageDir.deleteRecursively()
                workDir.deleteRecursively()
                output.delete()
            }
        }
    }

    @Test fun nearBlackHeuristicDistinguishesDarkAndGreenSamples() {
        val bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLACK)
            assertTrue(com.localcut.editor.bridge.agent.AndroidBridgeExportFileReviewer.isNearBlackSample(bitmap))
            bitmap.eraseColor(Color.GREEN)
            assertTrue(!com.localcut.editor.bridge.agent.AndroidBridgeExportFileReviewer.isNearBlackSample(bitmap))
        } finally { bitmap.recycle() }
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
