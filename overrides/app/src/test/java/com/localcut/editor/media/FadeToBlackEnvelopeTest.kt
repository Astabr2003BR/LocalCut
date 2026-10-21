package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FadeToBlackEnvelopeTest {
    @Test
    fun fadeInMovesFromBlackToFullBrightness() {
        val envelope = FadeToBlackEnvelope(
            durationUs = 4_000_000L,
            fadeInUs = 1_000_000L,
            fadeOutUs = 0L,
        )

        assertEquals(0f, envelope.brightnessAt(0L), 0.0001f)
        assertEquals(0.5f, envelope.brightnessAt(500_000L), 0.0001f)
        assertEquals(1f, envelope.brightnessAt(1_000_000L), 0.0001f)
        assertEquals(1f, envelope.brightnessAt(2_000_000L), 0.0001f)
    }

    @Test
    fun fadeOutMovesFromFullBrightnessToBlack() {
        val envelope = FadeToBlackEnvelope(
            durationUs = 4_000_000L,
            fadeInUs = 0L,
            fadeOutUs = 1_000_000L,
        )

        assertEquals(1f, envelope.brightnessAt(2_000_000L), 0.0001f)
        assertEquals(1f, envelope.brightnessAt(3_000_000L), 0.0001f)
        assertEquals(0.5f, envelope.brightnessAt(3_500_000L), 0.0001f)
        assertEquals(0f, envelope.brightnessAt(4_000_000L), 0.0001f)
    }

    @Test
    fun combinedFadeInAndOutKeepsMiddleFullyVisible() {
        val envelope = FadeToBlackEnvelope(
            durationUs = 4_000_000L,
            fadeInUs = 1_000_000L,
            fadeOutUs = 1_000_000L,
        )

        assertEquals(0.5f, envelope.brightnessAt(500_000L), 0.0001f)
        assertEquals(1f, envelope.brightnessAt(2_000_000L), 0.0001f)
        assertEquals(0.5f, envelope.brightnessAt(3_500_000L), 0.0001f)
    }

    @Test
    fun timestampsClampSafelyAtItemBoundaries() {
        val envelope = FadeToBlackEnvelope(2_000_000L, 500_000L, 500_000L)

        assertEquals(0f, envelope.brightnessAt(-10L), 0.0001f)
        assertEquals(0f, envelope.brightnessAt(2_500_000L), 0.0001f)
    }

    @Test
    fun overlappingOrInvalidFadeWindowsAreRejected() {
        listOf(
            Triple(0L, 0L, 0L),
            Triple(2_000_000L, -1L, 0L),
            Triple(2_000_000L, 0L, -1L),
            Triple(2_000_000L, 1_500_000L, 1_000_000L),
        ).forEach { (durationUs, fadeInUs, fadeOutUs) ->
            try {
                FadeToBlackEnvelope(durationUs, fadeInUs, fadeOutUs)
                fail("invalid envelope should fail: $durationUs/$fadeInUs/$fadeOutUs")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }
}
