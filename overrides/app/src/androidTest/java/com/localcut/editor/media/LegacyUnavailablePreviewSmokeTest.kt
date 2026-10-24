package com.localcut.editor.media

import android.net.Uri
import android.os.SystemClock
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
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class LegacyUnavailablePreviewSmokeTest {
    @Test
    fun unavailableLegacyImageDoesNotLeavePreviewAtZeroDuration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val durationUs = 5_000_000L
        val missing = missingImage(context.packageName)
        val base = Project.create("legacy-preview", 1080, 1920, 30)
        val project = base.copy(
            assets = listOf(missing),
            tracks = base.tracks.map { track ->
                if (track.type == TrackType.VISUAL) {
                    track.copy(clips = listOf(imageClip(missing.id, durationUs)))
                } else {
                    track
                }
            },
        )

        assertPreviewLoadsWithTimelineDuration(project, durationUs)
    }

    @Test
    fun unavailableLegacyImageWithValidAudioDoesNotLeavePreviewAtZeroDuration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val durationUs = 5_000_000L
        val audioDurationUs = 250_000L
        val missing = missingImage(context.packageName)
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val wav = createPcmWav(File(directory, "legacy-preview-${System.nanoTime()}.wav"))
        val audioUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.remote-media",
            wav,
        )
        val audio = Asset(
            id = "valid-audio",
            type = AssetType.AUDIO,
            uri = audioUri.toString(),
            sourceDurationUs = audioDurationUs,
            sizeBytes = wav.length(),
            displayName = wav.name,
            mimeType = "audio/wav",
            fingerprint = "valid-audio-${wav.length()}",
        )
        val base = Project.create("legacy-preview-with-audio", 1080, 1920, 30)
        val project = base.copy(
            assets = listOf(missing, audio),
            tracks = base.tracks.map { track ->
                when (track.type) {
                    TrackType.VISUAL -> track.copy(clips = listOf(imageClip(missing.id, durationUs)))
                    TrackType.AUDIO -> track.copy(
                        clips = listOf(
                            Clip(
                                id = "audio-clip",
                                assetId = audio.id,
                                startUs = 0L,
                                durationUs = audioDurationUs,
                                sourceInUs = 0L,
                                sourceOutUs = audioDurationUs,
                            ),
                        ),
                    )
                }
            },
        )

        try {
            assertPreviewLoadsWithTimelineDuration(project, durationUs)
        } finally {
            wav.delete()
        }
    }

    private fun assertPreviewLoadsWithTimelineDuration(project: Project, expectedDurationUs: Long) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var controller: Media3PreviewController
        val loadResult = AtomicReference<Result<Unit>>()
        instrumentation.runOnMainSync {
            controller = Media3PreviewController(
                context,
                ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
            )
            loadResult.set(controller.load(project))
        }

        try {
            assertTrue(
                "legacy unavailable media must not make preview.load fail: ${loadResult.get().exceptionOrNull()}",
                loadResult.get().isSuccess,
            )
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250)
            assertEquals(
                "timeline duration must remain visible even when a legacy asset is unavailable",
                expectedDurationUs,
                controller.state.value.durationUs,
            )
            assertNull(
                "unavailable legacy media must not poison the entire preview player",
                controller.state.value.errorMessage,
            )
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }

    private fun missingImage(packageName: String): Asset = Asset(
        id = "legacy-image",
        type = AssetType.IMAGE,
        uri = Uri.parse("content://$packageName.missing/${System.nanoTime()}.jpg").toString(),
        sourceDurationUs = null,
        sizeBytes = 100L,
        displayName = "218803.jpg",
        mimeType = "image/jpeg",
        fingerprint = "legacy-missing",
    )

    private fun imageClip(assetId: String, durationUs: Long): Clip = Clip(
        id = "legacy-clip",
        assetId = assetId,
        startUs = 0L,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
    )

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
}
