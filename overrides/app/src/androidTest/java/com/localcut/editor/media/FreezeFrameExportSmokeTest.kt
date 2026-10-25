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
import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.time.TimeMapEvaluator
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class FreezeFrameExportSmokeTest {
    @Test
    fun exportedFrameHoldPreservesDurationAndVisibleTimelineOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "freeze-frame-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val redAsset = imageAsset("red", createSolidPng(File(testDir, "red.png"), Color.RED))
            val greenAsset = imageAsset("green", createSolidPng(File(testDir, "green.png"), Color.GREEN))
            val blueAsset = imageAsset("blue", createSolidPng(File(testDir, "blue.png"), Color.BLUE))
            val baselineProject = Project(
                id = "freeze-baseline",
                schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
                title = "Freeze baseline",
                width = WIDTH,
                height = HEIGHT,
                fps = FPS,
                assets = listOf(redAsset, greenAsset, blueAsset),
                tracks = listOf(
                    Track(
                        id = "visual-1",
                        type = TrackType.VISUAL,
                        order = 0,
                        collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                        clips = listOf(
                            Clip("red-clip", redAsset.id, 0L, COLOR_DURATION_US, 0L, COLOR_DURATION_US),
                            Clip("green-clip", greenAsset.id, COLOR_DURATION_US, COLOR_DURATION_US, 0L, COLOR_DURATION_US),
                            Clip("blue-clip", blueAsset.id, 2L * COLOR_DURATION_US, COLOR_DURATION_US, 0L, COLOR_DURATION_US),
                        ),
                    ),
                    Track("audio-1", TrackType.AUDIO, 1, TrackCollisionPolicy.EXCLUSIVE),
                ),
            )

            val baselineVideo = File(testDir, "baseline.mp4")
            export(baselineProject, baselineVideo, instrumentation, context)
            val baselineDurationUs = readDurationUs(baselineVideo)
            assertTrue(
                "baseline video must preserve all three color sections: $baselineDurationUs",
                baselineDurationUs >= 2_800_000L,
            )

            val sourceRangeUs = baselineDurationUs.coerceAtMost(3L * COLOR_DURATION_US)
            val freezeAtUs = 1_500_000L
            assertTrue("baseline source is too short for freeze smoke", sourceRangeUs > 2_500_000L)
            val timeMap = TimeMap(
                freezeSegments = listOf(
                    FreezeSegment(
                        startOffsetUs = freezeAtUs,
                        durationUs = HOLD_DURATION_US,
                        sourceOffsetUs = freezeAtUs,
                    ),
                ),
            )
            val expectedDurationUs = TimeMapEvaluator.presentationDurationUs(sourceRangeUs, timeMap)
            val videoAsset = Asset(
                id = "video-source",
                type = AssetType.VIDEO,
                uri = Uri.fromFile(baselineVideo).toString(),
                sourceDurationUs = baselineDurationUs,
                sizeBytes = baselineVideo.length(),
                displayName = baselineVideo.name,
                mimeType = "video/mp4",
                fingerprint = "video-${baselineVideo.length()}",
            )
            val freezeProject = Project(
                id = "freeze-export",
                schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
                title = "Freeze export",
                width = WIDTH,
                height = HEIGHT,
                fps = FPS,
                assets = listOf(videoAsset),
                tracks = listOf(
                    Track(
                        id = "visual-1",
                        type = TrackType.VISUAL,
                        order = 0,
                        collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                        clips = listOf(
                            Clip(
                                id = "freeze-clip",
                                assetId = videoAsset.id,
                                startUs = 0L,
                                durationUs = expectedDurationUs,
                                sourceInUs = 0L,
                                sourceOutUs = sourceRangeUs,
                                timeMap = timeMap,
                            ),
                        ),
                    ),
                    Track("audio-1", TrackType.AUDIO, 1, TrackCollisionPolicy.EXCLUSIVE),
                ),
            )

            val frozenVideo = File(testDir, "frozen.mp4")
            export(freezeProject, frozenVideo, instrumentation, context)
            val actualDurationUs = readDurationUs(frozenVideo)
            val deltaUs = abs(actualDurationUs - expectedDurationUs)
            assertTrue(
                "freeze export duration diverged: expected=$expectedDurationUs actual=$actualDurationUs delta=$deltaUs",
                deltaUs <= DURATION_TOLERANCE_US,
            )

            assertDominantColor(extractFrame(frozenVideo, 500_000L), Dominant.RED)
            assertDominantColor(extractFrame(frozenVideo, freezeAtUs + 250_000L), Dominant.GREEN)
            assertDominantColor(extractFrame(frozenVideo, freezeAtUs + 750_000L), Dominant.GREEN)
            assertDominantColor(extractFrame(frozenVideo, expectedDurationUs - 500_000L), Dominant.BLUE)
        } finally {
            testDir.deleteRecursively()
        }
    }

    private fun assertDominantColor(frame: Bitmap, expected: Dominant) {
        try {
            val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val pass = when (expected) {
                Dominant.RED -> red >= 160 && red >= green + 80 && red >= blue + 80
                Dominant.GREEN -> green >= 120 && green >= red + 60 && green >= blue + 60
                Dominant.BLUE -> blue >= 160 && blue >= red + 80 && blue >= green + 80
            }
            assertTrue("expected $expected dominant frame, rgb=($red,$green,$blue)", pass)
        } finally {
            frame.recycle()
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

    private fun readDurationUs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Unable to read MP4 duration")
            durationMs * 1_000L
        } finally {
            retriever.release()
        }
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

    private enum class Dominant { RED, GREEN, BLUE }

    private companion object {
        const val WIDTH = 160
        const val HEIGHT = 90
        const val FPS = 30
        const val COLOR_DURATION_US = 1_000_000L
        const val HOLD_DURATION_US = 1_000_000L
        const val DURATION_TOLERANCE_US = 200_000L
    }
}
