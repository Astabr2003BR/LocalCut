package com.localcut.editor.mobile

import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.BuildContract
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.MainActivity
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.media.ProjectCompositionCompiler
import com.localcut.editor.media.RenderMode
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class SharedMediaImportSmokeTest {
    @Test
    fun importsColdAndWarmSharedImagesExactlyOnceAndRetainsPrivateCopies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var runtime: LocalCutProcessRuntime
        instrumentation.runOnMainSync {
            runtime = LocalCutProcessRuntime.get(context)
        }

        val before = visualClipCount(runtime)
        val beforeAssetIds = runtime.session.state.project.assets.map { it.id }.toSet()
        val coldUri = createPngFixture()
        val warmUri = createPngFixture()

        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, coldUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        ) as MainActivity

        waitForVisualClipCount(runtime, before + 1)
        assertEquals(before + 1, visualClipCount(runtime))
        assertFalse(activity.isFinishing)

        val coldAsset = newAssetsSince(runtime, beforeAssetIds).single()
        assertNotEquals(
            "shared media must not keep the transient source URI",
            coldUri.toString(),
            coldAsset.uri,
        )
        assertEquals(1, context.contentResolver.delete(coldUri, null, null))
        assertTrue(
            "cold shared media must still resolve after deleting the source fixture",
            AssetUriResolver(context.contentResolver).resolve(coldAsset).isSuccess,
        )
        waitForPreviewToMatchProject(runtime)

        val afterColdAssetIds = runtime.session.state.project.assets.map { it.id }.toSet()
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, warmUri)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            },
        )
        instrumentation.waitForIdleSync()
        waitForVisualClipCount(runtime, before + 2)
        SystemClock.sleep(250)

        assertEquals(before + 2, visualClipCount(runtime))
        assertFalse(activity.isFinishing)

        val warmAsset = newAssetsSince(runtime, afterColdAssetIds).single()
        assertNotEquals(
            "warm shared media must not keep the transient source URI",
            warmUri.toString(),
            warmAsset.uri,
        )
        assertEquals(1, context.contentResolver.delete(warmUri, null, null))
        assertTrue(
            "warm shared media must still resolve after deleting the source fixture",
            AssetUriResolver(context.contentResolver).resolve(warmAsset).isSuccess,
        )
        waitForPreviewToMatchProject(runtime)
    }

    @Test
    fun sharedImageVideoAndAudioUsePrivateBackingAndRemainPlayableAfterSourceDeletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var runtime: LocalCutProcessRuntime
        instrumentation.runOnMainSync {
            runtime = LocalCutProcessRuntime.get(context)
        }

        val testDir = File(context.cacheDir, "shared-kinds-${System.nanoTime()}").apply { mkdirs() }
        val beforeIds = runtime.session.state.project.assets.map { it.id }.toSet()
        val beforeVisual = visualClipCount(runtime)
        val beforeAudio = audioClipCount(runtime)
        val png = createSolidPng(File(testDir, "shared-image.png"))
        val wav = createPcmWav(File(testDir, "shared-audio.wav"))
        val mp4 = createMp4FromImage(
            image = png,
            output = File(testDir, "shared-video.mp4"),
            instrumentation = instrumentation,
            context = context,
        )
        val imageUri = publishFixture(png, "image/png", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videoUri = publishFixture(mp4, "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val audioUri = publishFixture(wav, "audio/wav", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

        try {
            val result = runBlocking {
                runtime.mediaIngestCoordinator.importSharedUris(listOf(imageUri, videoUri, audioUri))
            }

            assertEquals(3, result.importedCount)
            assertEquals(result.errors.joinToString(), 0, result.failedCount)
            assertEquals(beforeVisual + 2, visualClipCount(runtime))
            assertEquals(beforeAudio + 1, audioClipCount(runtime))

            val newAssets = newAssetsSince(runtime, beforeIds)
            assertEquals(3, newAssets.size)
            assertEquals(
                setOf(AssetType.IMAGE, AssetType.VIDEO, AssetType.AUDIO),
                newAssets.map { it.type }.toSet(),
            )
            val sourceUris = setOf(imageUri.toString(), videoUri.toString(), audioUri.toString())
            newAssets.forEach { asset ->
                assertTrue("shared asset must use LocalCut-owned backing", asset.uri !in sourceUris)
            }

            assertEquals(1, context.contentResolver.delete(imageUri, null, null))
            assertEquals(1, context.contentResolver.delete(videoUri, null, null))
            assertEquals(1, context.contentResolver.delete(audioUri, null, null))

            val resolver = AssetUriResolver(context.contentResolver)
            newAssets.forEach { asset ->
                assertTrue(
                    "${asset.type} backing must resolve after original share source is deleted",
                    resolver.resolve(asset).isSuccess,
                )
            }
            waitForPreviewToMatchProject(runtime)
            assertTrue(runtime.previewController.state.value.durationUs > 0L)
        } finally {
            runCatching { context.contentResolver.delete(imageUri, null, null) }
            runCatching { context.contentResolver.delete(videoUri, null, null) }
            runCatching { context.contentResolver.delete(audioUri, null, null) }
            testDir.deleteRecursively()
        }
    }

    private fun visualClipCount(runtime: LocalCutProcessRuntime): Int =
        runtime.session.state.project.tracks
            .filter { it.type == TrackType.VISUAL }
            .sumOf { it.clips.size }

    private fun audioClipCount(runtime: LocalCutProcessRuntime): Int =
        runtime.session.state.project.tracks
            .filter { it.type == TrackType.AUDIO }
            .sumOf { it.clips.size }

    private fun newAssetsSince(runtime: LocalCutProcessRuntime, ids: Set<String>): List<Asset> =
        runtime.session.state.project.assets.filter { it.id !in ids }

    private fun waitForVisualClipCount(runtime: LocalCutProcessRuntime, expected: Int) {
        repeat(50) {
            if (visualClipCount(runtime) == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, visualClipCount(runtime))
    }

    private fun waitForPreviewToMatchProject(runtime: LocalCutProcessRuntime) {
        repeat(50) {
            val expected = TimelineMath.durationUs(runtime.session.state.project)
            val actual = runtime.previewController.state.value.durationUs
            if (expected > 0L && actual == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(
            TimelineMath.durationUs(runtime.session.state.project),
            runtime.previewController.state.value.durationUs,
        )
    }

    private fun createPngFixture(): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "localcut-share-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "failed to create MediaStore fixture" }

        resolver.openOutputStream(uri)?.use { output ->
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            try {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } finally {
                bitmap.recycle()
            }
        } ?: error("failed to open MediaStore fixture")

        return uri
    }

    private fun publishFixture(file: File, mimeType: String, collection: Uri): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "localcut-share-${UUID.randomUUID()}-${file.name}")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }
        val uri = requireNotNull(context.contentResolver.insert(collection, values)) {
            "failed to publish ${file.name}"
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("failed to write ${file.name}")
        return uri
    }

    private fun createSolidPng(file: File): File {
        val bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file
    }

    private fun createPcmWav(file: File): File {
        val sampleRate = 8_000
        val channels = 1
        val bitsPerSample = 16
        val sampleCount = sampleRate / 4
        val dataSize = sampleCount * channels * (bitsPerSample / 8)
        file.outputStream().buffered().use { output ->
            output.write("RIFF".toByteArray())
            writeLe32(output, 36 + dataSize)
            output.write("WAVE".toByteArray())
            output.write("fmt ".toByteArray())
            writeLe32(output, 16)
            writeLe16(output, 1)
            writeLe16(output, channels)
            writeLe32(output, sampleRate)
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            writeLe32(output, byteRate)
            writeLe16(output, channels * (bitsPerSample / 8))
            writeLe16(output, bitsPerSample)
            output.write("data".toByteArray())
            writeLe32(output, dataSize)
            repeat(dataSize) { output.write(0) }
        }
        return file
    }

    private fun createMp4FromImage(
        image: File,
        output: File,
        instrumentation: Instrumentation,
        context: Context,
    ): File {
        val asset = Asset(
            id = "shared-smoke-image-${System.nanoTime()}",
            type = AssetType.IMAGE,
            uri = Uri.fromFile(image).toString(),
            sourceDurationUs = null,
            sizeBytes = image.length(),
            displayName = image.name,
            mimeType = "image/png",
            fingerprint = "shared-smoke-${image.length()}",
        )
        val project = Project(
            id = "shared-smoke-project-${System.nanoTime()}",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Shared media smoke",
            width = TEST_WIDTH,
            height = TEST_HEIGHT,
            fps = 30,
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip(
                            id = "clip-1",
                            assetId = asset.id,
                            startUs = 0L,
                            durationUs = TEST_VIDEO_DURATION_US,
                            sourceInUs = 0L,
                            sourceOutUs = TEST_VIDEO_DURATION_US,
                        ),
                    ),
                ),
                Track(
                    id = "audio-1",
                    type = TrackType.AUDIO,
                    order = 1,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                ),
            ),
        )
        val composition = ProjectCompositionCompiler(AssetUriResolver(context.contentResolver))
            .compile(project, RenderMode.EXPORT)
            .getOrThrow()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        instrumentation.runOnMainSync {
            Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        completed.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        failure.set(exportException)
                        completed.countDown()
                    }
                })
                .build()
                .start(composition, output.absolutePath)
        }
        check(completed.await(120, TimeUnit.SECONDS)) { "shared media MP4 fixture export timed out" }
        failure.get()?.let { throw AssertionError("shared media MP4 fixture export failed", it) }
        check(output.isFile && output.length() > 0L) { "shared media MP4 fixture missing" }
        return output
    }

    private fun writeLe16(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }

    private fun writeLe32(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 24) and 0xFF)
    }

    private companion object {
        const val TEST_WIDTH = 160
        const val TEST_HEIGHT = 90
        const val TEST_VIDEO_DURATION_US = 500_000L
    }
}
