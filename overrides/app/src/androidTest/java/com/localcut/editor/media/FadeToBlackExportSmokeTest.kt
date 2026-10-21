package com.localcut.editor.media

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class FadeToBlackExportSmokeTest {
    @Test
    fun exportVisiblyDimsOutgoingClipMidFade() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "fade-to-black-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val redFile = createSolidPng(File(testDir, "red.png"), Color.RED)
            val blueFile = createSolidPng(File(testDir, "blue.png"), Color.BLUE)
            val redAsset = imageAsset("red", redFile)
            val blueAsset = imageAsset("blue", blueFile)
            val project = Project(
                id = "fade-to-black-smoke",
                schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
                title = "Fade to black smoke",
                width = WIDTH,
                height = HEIGHT,
                fps = 30,
                assets = listOf(redAsset, blueAsset),
                tracks = listOf(
                    Track(
                        id = "visual-1",
                        type = TrackType.VISUAL,
                        order = 0,
                        collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                        clips = listOf(
                            Clip("a", redAsset.id, 0L, CLIP_DURATION_US, 0L, CLIP_DURATION_US),
                            Clip("b", blueAsset.id, CLIP_DURATION_US, CLIP_DURATION_US, 0L, CLIP_DURATION_US),
                        ),
                    ),
                    Track("audio-1", TrackType.AUDIO, 1, TrackCollisionPolicy.EXCLUSIVE),
                ),
                transitions = listOf(
                    ClipTransition(
                        id = "fade-a-b",
                        fromClipId = "a",
                        toClipId = "b",
                        durationUs = FADE_US,
                        kind = TransitionKind.FADE_TO_BLACK,
                    ),
                ),
            )

            val output = File(testDir, "fade.mp4")
            export(project, output, instrumentation, context)
            val frame = extractFrame(output, 750_000L)
            try {
                val center = frame.getPixel(frame.width / 2, frame.height / 2)
                val red = Color.red(center)
                val green = Color.green(center)
                val blue = Color.blue(center)
                assertTrue(
                    "fade-to-black must visibly dim outgoing red; rgb=($red,$green,$blue)",
                    red in 70..210 && green <= 45 && blue <= 45,
                )
            } finally {
                frame.recycle()
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    private fun export(
        project: Project,
        outputFile: File,
        instrumentation: Instrumentation,
        context: Context,
    ) {
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
                .start(composition, outputFile.absolutePath)
        }
        assertTrue("Transformer export timed out", completed.await(120, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Transformer export failed", it) }
        assertTrue("Exported MP4 is missing or empty", outputFile.isFile && outputFile.length() > 0L)
    }

    private fun extractFrame(file: File, timeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("Unable to decode exported frame at $timeUs us")
        } finally {
            retriever.release()
        }
    }

    private fun imageAsset(id: String, file: File): Asset = Asset(
        id = id,
        type = AssetType.IMAGE,
        uri = Uri.fromFile(file).toString(),
        sourceDurationUs = null,
        sizeBytes = file.length(),
        displayName = file.name,
        mimeType = "image/png",
        fingerprint = "$id-${file.length()}",
    )

    private fun createSolidPng(file: File, color: Int): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val CLIP_DURATION_US = 1_000_000L
        const val FADE_US = 500_000L
    }
}
