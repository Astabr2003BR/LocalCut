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
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class KeyframeExportSmokeTest {
    @Test
    fun secondLayerOpacityUsesClipLocalKeyframeTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "keyframe-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val blackFile = createSolidPng(File(testDir, "black.png"), Color.BLACK)
            val whiteFile = createSolidPng(File(testDir, "white.png"), Color.WHITE)
            val blackAsset = imageAsset("black", blackFile)
            val whiteAsset = imageAsset("white", whiteFile)
            val overlayKeyframes = listOf(
                TransformKeyframe(0L, TransformValue(opacity = 0.2)),
                TransformKeyframe(1_000_000L, TransformValue(opacity = 1.0)),
            )
            val project = Project(
                id = "keyframe-smoke",
                schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
                title = "Keyframe smoke",
                width = WIDTH,
                height = HEIGHT,
                fps = 30,
                assets = listOf(blackAsset, whiteAsset),
                tracks = listOf(
                    Track(
                        id = "visual-base",
                        type = TrackType.VISUAL,
                        order = 0,
                        collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                        clips = listOf(
                            Clip("base", blackAsset.id, 0L, 2_000_000L, 0L, 2_000_000L),
                        ),
                    ),
                    Track(
                        id = "visual-overlay",
                        type = TrackType.VISUAL,
                        order = 1,
                        collisionPolicy = TrackCollisionPolicy.OVERLAY,
                        clips = listOf(
                            Clip(
                                id = "overlay",
                                assetId = whiteAsset.id,
                                startUs = 1_000_000L,
                                durationUs = 1_000_000L,
                                sourceInUs = 0L,
                                sourceOutUs = 1_000_000L,
                                keyframes = overlayKeyframes,
                            ),
                        ),
                    ),
                    Track("audio-1", TrackType.AUDIO, 2, TrackCollisionPolicy.EXCLUSIVE),
                ),
            )

            val output = File(testDir, "keyframes.mp4")
            export(project, output, instrumentation, context)

            val early = extractFrame(output, 1_250_000L)
            val late = extractFrame(output, 1_750_000L)
            try {
                val earlyValue = centerLuma(early)
                val lateValue = centerLuma(late)
                assertTrue(
                    "early animated opacity must be visibly translucent; early=$earlyValue late=$lateValue",
                    earlyValue in 45..165,
                )
                assertTrue(
                    "later keyframe opacity must be substantially brighter; early=$earlyValue late=$lateValue",
                    lateValue >= earlyValue + 55 && lateValue >= 170,
                )
            } finally {
                early.recycle()
                late.recycle()
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    private fun centerLuma(frame: Bitmap): Int {
        val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        assertTrue("keyframe smoke must stay neutral grayscale; rgb=($red,$green,$blue)", maxOf(red, green, blue) - minOf(red, green, blue) <= 20)
        return (red + green + blue) / 3
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
    }
}
