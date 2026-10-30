package com.localcut.editor.media

import com.localcut.editor.domain.model.AudioDuckingSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDuckingPlannerTest {
    @Test
    fun overlapCompilesToClipLocalWindowWithoutChangingTargetTiming() {
        val target = clip(id = "music", startUs = 1_000_000L, durationUs = 4_000_000L)
        val source = Track(
            id = "voice-track",
            type = TrackType.AUDIO,
            order = 0,
            clips = listOf(clip(id = "voice", startUs = 2_000_000L, durationUs = 1_000_000L)),
        )
        val settings = AudioDuckingSettings(
            sourceTrackId = source.id,
            reductionDb = -12.0,
            attackUs = 100_000L,
            releaseUs = 250_000L,
        )

        val envelope = AudioDuckingPlanner.plan(target, source, settings)

        assertEquals(1, envelope.windows.size)
        assertEquals(1_000_000L, envelope.windows.single().sourceStartUs)
        assertEquals(2_000_000L, envelope.windows.single().sourceEndUs)
        assertEquals(4_000_000L, target.durationUs)
    }

    @Test
    fun releaseTailIsPreservedWhenSourceEndsImmediatelyBeforeTargetStarts() {
        val target = clip(id = "music", startUs = 1_000_000L, durationUs = 2_000_000L)
        val source = Track(
            id = "voice-track",
            type = TrackType.AUDIO,
            order = 0,
            clips = listOf(clip(id = "voice", startUs = 700_000L, durationUs = 200_000L)),
        )
        val settings = AudioDuckingSettings(
            sourceTrackId = source.id,
            reductionDb = -18.0,
            attackUs = 50_000L,
            releaseUs = 250_000L,
        )

        val envelope = AudioDuckingPlanner.plan(target, source, settings)

        assertEquals(1, envelope.windows.size)
        assertEquals(-300_000L, envelope.windows.single().sourceStartUs)
        assertEquals(-100_000L, envelope.windows.single().sourceEndUs)
        assertTrue(envelope.gainAtUs(0L) < 1f)
        assertEquals(1f, envelope.gainAtUs(200_000L), 0.0001f)
    }

    @Test
    fun nonOverlappingSourceProducesUnityEnvelope() {
        val target = clip(id = "music", startUs = 3_000_000L, durationUs = 1_000_000L)
        val source = Track(
            id = "voice-track",
            type = TrackType.AUDIO,
            order = 0,
            clips = listOf(clip(id = "voice", startUs = 0L, durationUs = 500_000L)),
        )
        val settings = AudioDuckingSettings(
            sourceTrackId = source.id,
            reductionDb = -12.0,
            attackUs = 100_000L,
            releaseUs = 100_000L,
        )

        val envelope = AudioDuckingPlanner.plan(target, source, settings)

        assertTrue(envelope.windows.isEmpty())
        assertEquals(1f, envelope.gainAtUs(0L), 0.0001f)
    }

    @Test
    fun attackHoldAndReleaseAreContinuousAndBounded() {
        val envelope = AudioDuckingEnvelope(
            windows = listOf(
                AudioDuckingWindow(
                    sourceStartUs = 100_000L,
                    sourceEndUs = 500_000L,
                    attackUs = 100_000L,
                    releaseUs = 200_000L,
                    reductionDb = -12.0,
                ),
            ),
        )

        val before = envelope.gainAtUs(50_000L)
        val attackMiddle = envelope.gainAtUs(150_000L)
        val hold = envelope.gainAtUs(300_000L)
        val releaseMiddle = envelope.gainAtUs(600_000L)
        val after = envelope.gainAtUs(800_000L)

        assertEquals(1f, before, 0.0001f)
        assertTrue(attackMiddle < 1f && attackMiddle > hold)
        assertTrue(hold in 0f..1f)
        assertTrue(releaseMiddle > hold && releaseMiddle < 1f)
        assertEquals(1f, after, 0.0001f)
    }

    @Test
    fun indexedPlannerHandlesLargeUnsortedSidechainAndReturnsOnlyLocalOverlap() {
        val sourceClips = (0 until 1_000)
            .map { index ->
                clip(
                    id = "voice-$index",
                    startUs = index * 2_000_000L,
                    durationUs = 500_000L,
                )
            }
            .reversed()
        val source = Track(
            id = "voice-track",
            type = TrackType.AUDIO,
            order = 0,
            clips = sourceClips,
        )
        val settings = AudioDuckingSettings(
            sourceTrackId = source.id,
            reductionDb = -12.0,
            attackUs = 100_000L,
            releaseUs = 250_000L,
        )
        val targetStartUs = 990 * 2_000_000L
        val target = clip(id = "music", startUs = targetStartUs, durationUs = 1_000_000L)

        val envelope = AudioDuckingIndex(source, settings).plan(target)

        assertEquals(1, envelope.windows.size)
        assertEquals(0L, envelope.windows.single().sourceStartUs)
        assertEquals(500_000L, envelope.windows.single().sourceEndUs)
    }

    @Test
    fun indexRejectsNonMonotonicEffectEndsInsteadOfUsingInvalidBinarySearch() {
        val source = Track(
            id = "voice-track",
            type = TrackType.AUDIO,
            order = 0,
            clips = listOf(
                clip(id = "long", startUs = 0L, durationUs = 10_000_000L),
                clip(id = "nested", startUs = 1_000_000L, durationUs = 1_000_000L),
            ),
        )
        val settings = AudioDuckingSettings(
            sourceTrackId = source.id,
            reductionDb = -12.0,
            attackUs = 100_000L,
            releaseUs = 250_000L,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            AudioDuckingIndex(source, settings)
        }

        assertTrue(error.message.orEmpty().contains("monotonic", ignoreCase = true))
    }

    private fun clip(id: String, startUs: Long, durationUs: Long): Clip = Clip(
        id = id,
        assetId = "asset-$id",
        startUs = startUs,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
    )
}
