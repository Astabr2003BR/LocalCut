package com.localcut.editor.media

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckingGainProviderTest {
    @Test
    fun gainProviderTracksAttackHoldReleaseAndUnityWindows() {
        val provider = DuckingGainProvider(
            AudioDuckingEnvelope(
                windows = listOf(
                    AudioDuckingWindow(
                        sourceStartUs = 100_000L,
                        sourceEndUs = 500_000L,
                        attackUs = 100_000L,
                        releaseUs = 200_000L,
                        reductionDb = -12.0,
                    ),
                ),
            ),
        )
        val sampleRate = 1_000_000

        assertEquals(1f, provider.getGainFactorAtSamplePosition(50_000L, sampleRate), 0.0001f)
        val attack = provider.getGainFactorAtSamplePosition(150_000L, sampleRate)
        val hold = provider.getGainFactorAtSamplePosition(300_000L, sampleRate)
        val release = provider.getGainFactorAtSamplePosition(600_000L, sampleRate)
        assertTrue(attack < 1f && attack > hold)
        assertTrue(release > hold && release < 1f)
        assertEquals(1f, provider.getGainFactorAtSamplePosition(800_000L, sampleRate), 0.0001f)

        assertEquals(100_000L, provider.isUnityUntil(0L, sampleRate))
        assertEquals(C.TIME_UNSET, provider.isUnityUntil(300_000L, sampleRate))
        assertEquals(C.TIME_END_OF_SOURCE, provider.isUnityUntil(800_000L, sampleRate))
    }

    @Test
    fun invalidSampleInputsFailClosed() {
        val provider = DuckingGainProvider(AudioDuckingEnvelope())

        runCatching { provider.getGainFactorAtSamplePosition(-1L, 48_000) }
            .onSuccess { throw AssertionError("negative sample position must fail") }
        runCatching { provider.getGainFactorAtSamplePosition(0L, 0) }
            .onSuccess { throw AssertionError("zero sample rate must fail") }
    }
}
