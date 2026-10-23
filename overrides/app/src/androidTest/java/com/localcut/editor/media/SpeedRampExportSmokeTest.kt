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
import com.localcut.editor.domain.model.SpeedPoint
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
class SpeedRampExportSmokeTest {
    @Test
    fun exportedRampDurationMatchesLocalCutTimeMap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testDir = File(context.cacheDir, "speed-ramp-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val imageFile = createSolidPng(File(testDir, "source.png"), Color.rgb(48, 160, 224))
            val imageAsset = Asset(
                id = "image-source",
                type = AssetType.IMAGE,
                uri = Uri.fromFile(imageFile).toString(),
                sourceDurationUs = null,
                sizeBytes = imageFile.length(),
                displayName = imageFile.name,
                mimeType = "image/png",
                fingerprint = "image-${imageFile.length()}",
            )
            val baselineProject = Project(
                id = "speed-ramp-baseline",
                schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
                title = "Speed ramp baseline",
                width = WIDTH,
                height = HEIGHT,
                fps = 30,
                assets = listOf(imageAsset),
                tracks = listOf(
                    Track(
                        id = "visual-1",
                        type = TrackType.VISUAL,
                        order = 0,
                        collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                        clips = listOf(
                            Clip(
                                id = "image-clip",
                                assetId = imageAsset.id,
                                startUs = 0L,
                                durationUs = BASE_DURATION_US,
                                sourceInUs = 0L,
                                sourceOutUs = BASE_DURATION_US,
                            ),
                        ),
                    ),
                    Track("audio-1", TrackType.AUDIO, 1, TrackCollisionPolicy.EXCLUSIVE),
                ),
            )

            val baselineVideo = File(testDir, "baseline.mp4")
            export(baselineProject, baselineVideo, instrumentation, context)
            val baselineDurationUs = readDurationUs(baselineVideo)
            assertTrue("baseline video must have a useful duration: $baselineDurationUs", baselineDurationUs > 1_000_000L)

            val sourceRangeUs = baselineDurationUs.coerceAtMost(BASE_DURATION_US)
            val ramp = TimeMap(
                baseSpeed = 0.5,
                speedPoints = listOf(
                    SpeedPoint(0L, 0.5),
                    SpeedPoint(minOf(1_000_000L, sourceRangeUs / 3L).coerceAtLeast(1L), 2.0),
                ),
            )
            val expectedDurationUs = TimeMapEvaluator.presentationDurationUs(sourceRangeUs, ramp)
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
            val rampProject = Project(
                id = "speed-ramp-export",
                schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
                title = "Speed ramp export",
                width = WIDTH,
                height = HEIGHT,
                fps = 30,
                assets = listOf(videoAsset),
                tracks = listOf(
                    Track(
                        id = "visual-1",
                        type = TrackType.VISUAL,
                        order = 0,
                        collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                        clips = listOf(
                            Clip(
                                id = "ramp-clip",
                                assetId = videoAsset.id,
                                startUs = 0L,
                                durationUs = expectedDurationUs,
                                sourceInUs = 0L,
                                sourceOutUs = sourceRangeUs,
                                timeMap = ramp,
                            ),
                        ),
                    ),
                    Track("audio-1", TrackType.AUDIO, 1, TrackCollisionPolicy.EXCLUSIVE),
                ),
            )

            val rampVideo = File(testDir, "ramp.mp4")
            export(rampProject, rampVideo, instrumentation, context)
            val actualDurationUs = readDurationUs(rampVideo)
            val deltaUs = abs(actualDurationUs - expectedDurationUs)

            assertTrue(
                "speed-ramp export duration diverged: expected=$expectedDurationUs actual=$actualDurationUs delta=$deltaUs",
                deltaUs <= DURATION_TOLERANCE_US,
            )
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
        const val WIDTH = 160
        const val HEIGHT = 90
        const val BASE_DURATION_US = 3_000_000L
        const val DURATION_TOLERANCE_US = 200_000L
    }
}
