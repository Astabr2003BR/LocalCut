package com.localcut.editor.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioEnvelopeTest {
    @Test
    fun linearFadeEnvelopeHasDeterministicStartMiddleAndEndGain() {
        val envelope = AudioEnvelope(
            durationUs = 10_000_000L,
            fadeInUs = 2_000_000L,
            fadeOutUs = 3_000_000L,
        )

        assertEquals(0.0, envelope.gainFactorAt(0L), 0.000001)
        assertEquals(0.5, envelope.gainFactorAt(1_000_000L), 0.000001)
        assertEquals(1.0, envelope.gainFactorAt(2_000_000L), 0.000001)
        assertEquals(1.0, envelope.gainFactorAt(7_000_000L), 0.000001)
        assertEquals(0.5, envelope.gainFactorAt(8_500_000L), 0.000001)
        assertEquals(0.0, envelope.gainFactorAt(10_000_000L), 0.000001)
    }

    @Test
    fun zeroLengthFadesKeepUnityGainAcrossTheClip() {
        val envelope = AudioEnvelope(
            durationUs = 5_000_000L,
            fadeInUs = 0L,
            fadeOutUs = 0L,
        )

        assertEquals(1.0, envelope.gainFactorAt(0L), 0.000001)
        assertEquals(1.0, envelope.gainFactorAt(2_500_000L), 0.000001)
        assertEquals(1.0, envelope.gainFactorAt(5_000_000L), 0.000001)
    }

    @Test
    fun overlappingFadesAreRejectedInsteadOfSilentlyOverwritingAutomation() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioEnvelope(
                durationUs = 5_000_000L,
                fadeInUs = 3_000_000L,
                fadeOutUs = 3_000_000L,
            )
        }
    }
}
