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
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskPoint
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class CreativeShaderExportSmokeTest {
    @Test
    fun maskAndStylizeShadersCompileAndAffectExportedFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "creative-shader-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val whiteFile = createSolidPng(File(testDir, "white.png"), Color.WHITE)
            val asset = imageAsset("white", whiteFile)
            val clip = Clip(
                id = "creative",
                assetId = asset.id,
                startUs = 0L,
                durationUs = 1_000_000L,
                sourceInUs = 0L,
                sourceOutUs = 1_000_000L,
                compositing = CompositingSettings(
                    masks = listOf(MaskSpec(id = "ellipse", kind = MaskKind.ELLIPSE, featherPx = 4.0)),
                ),
                effects = listOf(
                    EffectInstance(
                        id = "vignette",
                        typeId = "localcut.vignette",
                        parameters = mapOf("amount" to EffectParameter.Number(0.7)),
                    ),
                ),
            )
            val project = singleImageProject("creative-shader-smoke", asset, clip)

            val output = File(testDir, "creative-shaders.mp4")
            export(project, output, instrumentation, context)
            val frame = extractFrame(output, 500_000L)
            try {
                val center = luma(frame.getPixel(frame.width / 2, frame.height / 2))
                val corner = luma(frame.getPixel(4, 4))
                assertTrue("center should remain bright; center=$center", center >= 170)
                assertTrue("mask/vignette should darken corner; center=$center corner=$corner", corner <= center - 80)
            } finally {
                frame.recycle()
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun freeformMaskShaderCompilesAndCutsOutsidePolygon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "freeform-mask-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val whiteFile = createSolidPng(File(testDir, "white.png"), Color.WHITE)
            val asset = imageAsset("freeform-white", whiteFile)
            val clip = Clip(
                id = "freeform",
                assetId = asset.id,
                startUs = 0L,
                durationUs = 1_000_000L,
                sourceInUs = 0L,
                sourceOutUs = 1_000_000L,
                compositing = CompositingSettings(
                    masks = listOf(
                        MaskSpec(
                            id = "diamond",
                            kind = MaskKind.FREEFORM,
                            points = listOf(
                                MaskPoint(0.50, 0.08),
                                MaskPoint(0.92, 0.50),
                                MaskPoint(0.50, 0.92),
                                MaskPoint(0.08, 0.50),
                            ),
                            featherPx = 2.0,
                        ),
                    ),
                ),
            )
            val project = singleImageProject("freeform-mask-smoke", asset, clip)

            val output = File(testDir, "freeform-mask.mp4")
            export(project, output, instrumentation, context)
            val frame = extractFrame(output, 500_000L)
            try {
                val center = luma(frame.getPixel(frame.width / 2, frame.height / 2))
                val corner = luma(frame.getPixel(4, 4))
                assertTrue("polygon center should remain visible; center=$center", center >= 180)
                assertTrue("outside polygon should be strongly cut; corner=$corner", corner <= 45)
            } finally {
                frame.recycle()
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    private fun singleImageProject(id: String, asset: Asset, clip: Clip): Project = Project(
        id = id,
        schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
        title = id,
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
                clips = listOf(clip),
            ),
            Track("audio-1", TrackType.AUDIO, 1, TrackCollisionPolicy.EXCLUSIVE),
        ),
    )

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
                ?: error("Unable to decode creative shader export frame")
        } finally {
            retriever.release()
        }
    }

    private fun luma(pixel: Int): Int = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3

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
