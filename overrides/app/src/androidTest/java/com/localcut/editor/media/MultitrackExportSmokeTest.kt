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
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
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
class MultitrackExportSmokeTest {
    @Test
    fun exportsTwoVisualLayersAndKeepsOverlayAboveBase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "multitrack-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val baseFile = createSolidPng(File(testDir, "base.png"), Color.RED)
            val overlayFile = createSolidPng(File(testDir, "overlay.png"), Color.BLUE)
            val project = projectFor(baseFile, overlayFile)
            val frame = exportFrame(project, File(testDir, "output.mp4"), instrumentation, context)
            try {
                val center = frame.getPixel(frame.width / 2, frame.height / 2)
                val corner = frame.getPixel(frame.width / 12, frame.height / 12)
                assertDominantBlue(center, "center pixel must come from the overlay layer")
                assertDominantRed(corner, "corner pixel must remain from the base layer")
            } finally {
                frame.recycle()
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun exportsTextOverlayIntoRenderedPixels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "text-overlay-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val baseFile = createSolidPng(File(testDir, "base-black.png"), Color.BLACK)
            val baseAsset = imageAsset("text-base", baseFile)
            val project = Project(
                id = "text-overlay-smoke",
                schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
                title = "Text overlay smoke",
                width = WIDTH,
                height = HEIGHT,
                fps = 30,
                assets = listOf(baseAsset),
                tracks = listOf(
                    Track(
                        id = "visual-base",
                        type = TrackType.VISUAL,
                        order = 0,
                        collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                        clips = listOf(
                            Clip(
                                id = "base-clip",
                                assetId = baseAsset.id,
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
                textOverlays = listOf(
                    TextOverlay(
                        id = "title",
                        startUs = 0L,
                        durationUs = DURATION_US,
                        text = "LOCALCUT",
                        style = TextStyle(fontSizePx = 72.0, fillArgb = Color.WHITE),
                        zIndex = 10,
                    ),
                ),
            )

            val frame = exportFrame(project, File(testDir, "text-output.mp4"), instrumentation, context)
            try {
                val brightPixels = countBrightPixels(frame)
                assertTrue(
                    "exported frame must contain visible text pixels; brightPixels=$brightPixels",
                    brightPixels >= 120,
                )
            } finally {
                frame.recycle()
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    private fun exportFrame(
        project: Project,
        outputFile: File,
        instrumentation: Instrumentation,
        context: Context,
    ): Bitmap {
        val compiler = ProjectCompositionCompiler(AssetUriResolver(context.contentResolver))
        val composition = compiler.compile(project, RenderMode.EXPORT).getOrThrow()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        instrumentation.runOnMainSync {
            val transformer = Transformer.Builder(context)
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
            transformer.start(composition, outputFile.absolutePath)
        }

        assertTrue("Transformer export timed out", completed.await(120, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Transformer export failed", it) }
        assertTrue("Exported MP4 is missing or empty", outputFile.isFile && outputFile.length() > 0L)

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(outputFile.absolutePath)
            retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Unable to decode exported frame")
        } finally {
            retriever.release()
        }
    }

    private fun projectFor(baseFile: File, overlayFile: File): Project {
        val baseAsset = imageAsset("base", baseFile)
        val overlayAsset = imageAsset("overlay", overlayFile)
        return Project(
            id = "multitrack-smoke",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Multitrack smoke",
            width = WIDTH,
            height = HEIGHT,
            fps = 30,
            assets = listOf(baseAsset, overlayAsset),
            tracks = listOf(
                Track(
                    id = "visual-base",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip(
                            id = "base-clip",
                            assetId = baseAsset.id,
                            startUs = 0L,
                            durationUs = DURATION_US,
                            sourceInUs = 0L,
                            sourceOutUs = DURATION_US,
                        ),
                    ),
                ),
                Track(
                    id = "visual-overlay",
                    type = TrackType.VISUAL,
                    order = 1,
                    collisionPolicy = TrackCollisionPolicy.OVERLAY,
                    clips = listOf(
                        Clip(
                            id = "overlay-clip",
                            assetId = overlayAsset.id,
                            startUs = 0L,
                            durationUs = DURATION_US,
                            sourceInUs = 0L,
                            sourceOutUs = DURATION_US,
                            transform = TransformValue(scale = 0.5),
                        ),
                    ),
                ),
                Track(
                    id = "audio-1",
                    type = TrackType.AUDIO,
                    order = 2,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                ),
            ),
        )
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

    private fun countBrightPixels(frame: Bitmap): Int {
        var count = 0
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val color = frame.getPixel(x, y)
                if (Color.red(color) >= 190 && Color.green(color) >= 190 && Color.blue(color) >= 190) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun assertDominantBlue(color: Int, message: String) {
        val blue = Color.blue(color)
        val red = Color.red(color)
        val green = Color.green(color)
        assertTrue("$message; rgb=($red,$green,$blue)", blue >= red + 40 && blue >= green + 40)
    }

    private fun assertDominantRed(color: Int, message: String) {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        assertTrue("$message; rgb=($red,$green,$blue)", red >= green + 40 && red >= blue + 40)
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val DURATION_US = 1_000_000L
    }
}
