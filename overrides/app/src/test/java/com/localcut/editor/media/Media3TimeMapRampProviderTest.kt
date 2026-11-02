package com.localcut.editor.media

import androidx.media3.common.C
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.time.TimeMapEvaluator
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3TimeMapRampProviderTest {
    @Test
    fun rampCompilerPreservesLocalCutDurationWithinMicrosecondTolerance() {
        val map = TimeMap(
            baseSpeed = 0.75,
            speedPoints = listOf(
                SpeedPoint(0L, 0.75),
                SpeedPoint(1_000_000L, 2.0),
                SpeedPoint(2_500_000L, 0.5),
            ),
        )
        val sourceRangeUs = 3_000_000L
        val expectedPresentationUs = TimeMapEvaluator.presentationDurationUs(sourceRangeUs, map)

        val provider = Media3TimeMapSupport.providerOrNull(map, sourceRangeUs)
            as SegmentedSpeedProvider

        val reconstructedUs = provider.segments.sumOf { segment ->
            ((segment.sourceEndUs - segment.sourceStartUs).toDouble() / segment.speed.toDouble()).toLong()
        }

        assertTrue(provider.segments.size > 2)
        assertEquals(0L, provider.segments.first().sourceStartUs)
        assertEquals(sourceRangeUs, provider.segments.last().sourceEndUs)
        assertTrue(
            "expected=$expectedPresentationUs reconstructed=$reconstructedUs",
            abs(expectedPresentationUs - reconstructedUs) <= provider.segments.size + 2L,
        )
    }

    @Test
    fun segmentedProviderChangesAtSourceBoundariesAndReturnsExpectedSpeeds() {
        val provider = SegmentedSpeedProvider(
            listOf(
                SpeedSegment(0L, 500_000L, 0.5f),
                SpeedSegment(500_000L, 1_500_000L, 2.0f),
            ),
        )

        assertEquals(0.5f, provider.getSpeed(0L), 0f)
        assertEquals(0.5f, provider.getSpeed(499_999L), 0f)
        assertEquals(500_000L, provider.getNextSpeedChangeTimeUs(0L))
        assertEquals(2.0f, provider.getSpeed(500_000L), 0f)
        assertEquals(C.TIME_UNSET, provider.getNextSpeedChangeTimeUs(500_000L))
    }

    @Test
    fun media3SupportsForwardRampButStillRejectsReverseAndFreeze() {
        assertTrue(
            Media3TimeMapSupport.canRender(
                TimeMap(speedPoints = listOf(SpeedPoint(0L, 1.0), SpeedPoint(1_000_000L, 2.0))),
            ),
        )
        assertFalse(Media3TimeMapSupport.canRender(TimeMap(reverse = true)))
        assertFalse(
            Media3TimeMapSupport.canRender(
                TimeMap(freezeSegments = listOf(com.localcut.editor.domain.model.FreezeSegment(0L, 100_000L, 0L))),
            ),
        )
    }
}
