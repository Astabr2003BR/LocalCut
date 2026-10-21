package com.localcut.editor.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.BuildContract
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.media.ProjectCompositionCompiler
import com.localcut.editor.media.RenderMode
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class RemoteMediaValidatorSmokeTest {
    @Test
    fun validatesRealMediaRejectsSpoofAndRestrictsProvider() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val remoteDir = File(context.cacheDir, "remote-import").apply { mkdirs() }
        val testDir = File(remoteDir, "validator-${System.nanoTime()}").apply { mkdirs() }
        val persistentDir = File(context.filesDir, "remote-media").apply { mkdirs() }
        val providerFile = File(persistentDir, "provider-${System.nanoTime()}.png")
        try {
            val png = createSolidPng(File(testDir, "image.png"))
            val wav = createPcmWav(File(testDir, "audio.wav"))
            val mp4 = createMp4FromImage(
                image = png,
                output = File(testDir, "video.mp4"),
                instrumentation = instrumentation,
                context = context,
            )
            val spoof = File(testDir, "fake.mp4").apply { writeText("not-media") }

            assertEquals(RemoteMediaKind.IMAGE, RemoteMediaValidator.validate(png, "image/png").getOrThrow())
            assertEquals(RemoteMediaKind.AUDIO, RemoteMediaValidator.validate(wav, "audio/wav").getOrThrow())
            assertEquals(RemoteMediaKind.VIDEO, RemoteMediaValidator.validate(mp4, "video/mp4").getOrThrow())
            assertTrue(RemoteMediaValidator.validate(spoof, "video/mp4").isFailure)
            assertTrue(RemoteMediaValidator.validate(png, "video/mp4").isFailure)

            png.copyTo(providerFile, overwrite = true)
            val allowedUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.remote-media",
                providerFile,
            )
            assertEquals("content", allowedUri.scheme)

            val cacheFile = File(context.cacheDir, "outside-remote-provider.png").apply {
                png.copyTo(this, overwrite = true)
            }
            val siblingPrivateFile = File(context.filesDir, "outside-remote-provider.png").apply {
                png.copyTo(this, overwrite = true)
            }
            try {
                assertTrue(
                    "cache download files must not be exposed by the persistent provider",
                    runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.remote-media",
                            cacheFile,
                        )
                    }.isFailure,
                )
                assertTrue(
                    "unrelated private files must not be exposed by the provider",
                    runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.remote-media",
                            siblingPrivateFile,
                        )
                    }.isFailure,
                )
            } finally {
                cacheFile.delete()
                siblingPrivateFile.delete()
            }
        } finally {
            testDir.deleteRecursively()
            providerFile.delete()
        }
    }

    private fun createSolidPng(file: File): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
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
            id = "remote-validator-image",
            type = AssetType.IMAGE,
            uri = Uri.fromFile(image).toString(),
            sourceDurationUs = null,
            sizeBytes = image.length(),
            displayName = image.name,
            mimeType = "image/png",
            fingerprint = "validator-${image.length()}",
        )
        val project = Project(
            id = "remote-validator-project",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Remote validator",
            width = WIDTH,
            height = HEIGHT,
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
                            durationUs = DURATION_US,
                            sourceInUs = 0L,
                            sourceOutUs = DURATION_US,
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
        check(completed.await(120, TimeUnit.SECONDS)) { "remote validator MP4 export timed out" }
        failure.get()?.let { throw AssertionError("remote validator MP4 export failed", it) }
        check(output.isFile && output.length() > 0L) { "remote validator MP4 missing" }
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
        const val WIDTH = 160
        const val HEIGHT = 90
        const val DURATION_US = 500_000L
    }
}
