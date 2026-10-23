package com.localcut.editor.audio

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBeatAnalyzerSmokeTest {
    @Test
    fun decodesLocalPcmAndFindsDeterministicPulseTrain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDir = File(context.cacheDir, "beat-analyzer-smoke-${System.nanoTime()}").apply { mkdirs() }
        try {
            val wav = createPulseTrainWav(
                file = File(testDir, "beats.wav"),
                durationUs = DURATION_US,
                pulseTimesUs = EXPECTED_BEATS_US,
            )

            val markers = AndroidBeatAnalyzer(context.contentResolver)
                .analyze(
                    uri = Uri.fromFile(wav).toString(),
                    sourceInUs = 0L,
                    sourceOutUs = DURATION_US,
                    settings = BeatDetectionSettings(
                        analysisWindowMs = 40,
                        minimumIntervalMs = 240,
                        thresholdMultiplier = 1.6,
                    ),
                )
                .getOrThrow()

            assertTrue("expected at least 3 detected beats, got ${markers.size}: $markers", markers.size >= 3)
            assertTrue("beat markers must be time-sorted", markers.zipWithNext().all { (a, b) -> a.timeUs <= b.timeUs })
            assertTrue("beat strengths must stay in 0..1", markers.all { it.strength in 0.0..1.0 })

            EXPECTED_BEATS_US.forEach { expectedUs ->
                val nearest = markers.minByOrNull { marker -> abs(marker.timeUs - expectedUs) }
                    ?: throw AssertionError("missing beat near $expectedUs us")
                assertTrue(
                    "expected beat near $expectedUs us but nearest was ${nearest.timeUs} us; markers=$markers",
                    abs(nearest.timeUs - expectedUs) <= BEAT_TOLERANCE_US,
                )
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    private fun createPulseTrainWav(
        file: File,
        durationUs: Long,
        pulseTimesUs: List<Long>,
    ): File {
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

        val pulseHalfWidthSamples = (sampleRate * 12 / 1_000).coerceAtLeast(1)
        val pulseCenters = pulseTimesUs.map { timeUs ->
            ((timeUs * sampleRate) / 1_000_000L).toInt()
        }
        repeat(sampleCount) { sampleIndex ->
            val inPulse = pulseCenters.any { center -> abs(sampleIndex - center) <= pulseHalfWidthSamples }
            val amplitude = if (inPulse) 0.92 else 0.004
            val sign = if (sampleIndex % 2 == 0) 1.0 else -1.0
            val sample = (amplitude * sign * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            bytes.putShort(sample.toShort())
        }
        file.writeBytes(bytes.array())
        return file
    }

    private companion object {
        const val DURATION_US = 2_000_000L
        const val BEAT_TOLERANCE_US = 100_000L
        val EXPECTED_BEATS_US = listOf(300_000L, 900_000L, 1_500_000L)
    }
}
