package com.localcut.editor.media

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.mobile.AndroidAudioSourceDurationProbe
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class ImageAudioPreviewSmokeTest {
    @Test
    fun staticImageWithAudioPlaysWithoutVideoFrameProcessingFailure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val imageFile = createPng(File(directory, "preview-image-${System.nanoTime()}.png"), Color.GREEN)
        val audioFile = createMonoPcmWav(File(directory, "preview-audio-${System.nanoTime()}.wav"), DURATION_US)
        val imageAsset = asset(
            packageName = context.packageName,
            id = "still",
            file = imageFile,
            type = AssetType.IMAGE,
            mimeType = "image/png",
            sourceDurationUs = null,
        )
        val audioAsset = asset(
            packageName = context.packageName,
            id = "sound",
            file = audioFile,
            type = AssetType.AUDIO,
            mimeType = "audio/wav",
            sourceDurationUs = DURATION_US,
        )
        val seed = Project.create("image-audio-preview", WIDTH, HEIGHT, 30)
        val visualTrack = seed.tracks.first { it.type == TrackType.VISUAL }.copy(
            clips = listOf(clip("still-clip", imageAsset.id, DURATION_US)),
        )
        val audioTrack = seed.tracks.first { it.type == TrackType.AUDIO }.copy(
            clips = listOf(clip("audio-clip", audioAsset.id, DURATION_US)),
        )
        val project = seed.copy(
            assets = listOf(imageAsset, audioAsset),
            tracks = listOf(visualTrack, audioTrack),
        )

        var controller: Media3PreviewController? = null
        try {
            instrumentation.runOnMainSync {
                val createdController = Media3PreviewController(
                    context,
                    ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
                )
                controller = createdController
                val load = createdController.load(project)
                assertTrue("image+audio preview must load: ${load.exceptionOrNull()}", load.isSuccess)
                createdController.play()
            }

            val activeController = checkNotNull(controller)
            val settled = runBlocking {
                withTimeout(15_000L) {
                    activeController.state.first { state ->
                        state.errorMessage != null || state.positionUs >= 250_000L
                    }
                }
            }

            assertNull(
                "image+audio preview must not report a video frame processing failure",
                settled.errorMessage,
            )
            assertTrue(
                "image+audio preview must advance playback; positionUs=${settled.positionUs}",
                settled.positionUs >= 250_000L,
            )
        } finally {
            controller?.let { activeController ->
                instrumentation.runOnMainSync { activeController.release() }
            }
            imageFile.delete()
            audioFile.delete()
        }
    }


    @Test
    fun localAudioDurationProbeReadsOwnedFileWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val wav = createMonoPcmWav(
            File(directory, "duration-probe-${System.nanoTime()}.wav"),
            1_000_000L,
        )
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.remote-media",
                wav,
            ).toString()
            val probe = AndroidAudioSourceDurationProbe(context)
            val measured = probe.readDurationUs(uri)
            assertTrue("owned 1s PCM WAV must report duration: $measured",
                measured != null && measured in 900_000L..1_100_000L)
            assertNull("network URIs must not be fetched",
                probe.readDurationUs("https://example.com/private.mp3"))
            assertNull("malformed file URI must not crash",
                probe.readDurationUs("file:///does-not-exist/missing.wav"))
        } finally {
            wav.delete()
        }
    }

    @Test
    fun rapidBackAndForthScrubbingKeepsImageAudioPreviewUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val imageFile = createPng(File(directory, "scrub-image-${System.nanoTime()}.png"), Color.BLUE)
        val audioFile = createMonoPcmWav(File(directory, "scrub-audio-${System.nanoTime()}.wav"), DURATION_US)
        val imageAsset = asset(context.packageName, "scrub-still", imageFile, AssetType.IMAGE, "image/png", null)
        val audioAsset = asset(context.packageName, "scrub-sound", audioFile, AssetType.AUDIO, "audio/wav", DURATION_US)
        val seed = Project.create("image-audio-scrub", WIDTH, HEIGHT, 30)
        val visualTrack = seed.tracks.first { it.type == TrackType.VISUAL }.copy(
            clips = listOf(clip("scrub-still-clip", imageAsset.id, DURATION_US)),
        )
        val audioTrack = seed.tracks.first { it.type == TrackType.AUDIO }.copy(
            clips = listOf(clip("scrub-sound-clip", audioAsset.id, DURATION_US)),
        )
        val project = seed.copy(
            assets = listOf(imageAsset, audioAsset),
            tracks = listOf(visualTrack, audioTrack),
        )

        var controller: Media3PreviewController? = null
        try {
            instrumentation.runOnMainSync {
                val created = Media3PreviewController(
                    context,
                    ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
                )
                controller = created
                assertTrue("scrub project must load", created.load(project).isSuccess)
                created.setScrubbingModeEnabled(true)
                repeat(60) { step ->
                    created.seekToUs(if (step % 2 == 0) 800_000L else 120_000L)
                }
                created.setScrubbingModeEnabled(false)
                created.seekToUs(300_000L)
                created.play()
            }
            // Observe asynchronous decoder / video graph failures after seek commands settle.
            runBlocking { kotlinx.coroutines.delay(1_500L) }
            val settled = checkNotNull(controller).state.value
            assertNull("rapid scrubbing must not fail video frames", settled.errorMessage)
            assertTrue("preview must remain loaded after scrubbing", settled.durationUs == DURATION_US)
        } finally {
            controller?.let { active ->
                instrumentation.runOnMainSync {
                    active.setScrubbingModeEnabled(false)
                    active.release()
                }
            }
            imageFile.delete()
            audioFile.delete()
        }
    }

    private fun clip(id: String, assetId: String, durationUs: Long): Clip = Clip(
        id = id,
        assetId = assetId,
        startUs = 0L,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
    )

    private fun asset(
        packageName: String,
        id: String,
        file: File,
        type: AssetType,
        mimeType: String,
        sourceDurationUs: Long?,
    ): Asset = Asset(
        id = id,
        type = type,
        uri = FileProvider.getUriForFile(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "$packageName.remote-media",
            file,
        ).toString(),
        sourceDurationUs = sourceDurationUs,
        sizeBytes = file.length(),
        displayName = file.name,
        mimeType = mimeType,
        fingerprint = "$id-${file.length()}",
    )

    private fun createPng(file: File, color: Int): File {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        bitmap.recycle()
        return file
    }

    private fun createMonoPcmWav(file: File, durationUs: Long): File {
        val sampleRate = 44_100
        val channels = 1
        val bitsPerSample = 16
        val sampleCount = ((durationUs * sampleRate) / 1_000_000L).toInt().coerceAtLeast(1)
        val dataSize = sampleCount * channels * (bitsPerSample / 8)
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII))
        bytes.putInt(36 + dataSize)
        bytes.put("WAVE".toByteArray(Charsets.US_ASCII))
        bytes.put("fmt ".toByteArray(Charsets.US_ASCII))
        bytes.putInt(16)
        bytes.putShort(1.toShort())
        bytes.putShort(channels.toShort())
        bytes.putInt(sampleRate)
        val byteRate = sampleRate * channels * bitsPerSample / 8
        bytes.putInt(byteRate)
        bytes.putShort((channels * bitsPerSample / 8).toShort())
        bytes.putShort(bitsPerSample.toShort())
        bytes.put("data".toByteArray(Charsets.US_ASCII))
        bytes.putInt(dataSize)
        repeat(sampleCount) { bytes.putShort(0) }
        file.writeBytes(bytes.array())
        return file
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val DURATION_US = 1_000_000L
    }
}
