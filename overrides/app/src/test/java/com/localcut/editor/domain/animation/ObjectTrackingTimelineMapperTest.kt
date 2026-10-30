package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingTimelineMapperTest {
    @Test
    fun identityKeepsSourceOffsets() {
        val mapped = ObjectTrackingTimelineMapper.toPresentationSamples(
            samples = listOf(
                ObjectTrackingSourceSample(100_000L, 0.01, 0.02, 0.9),
                ObjectTrackingSourceSample(300_000L, 0.03, 0.04, 0.8),
            ),
            sourceRangeUs = 1_000_000L,
            timeMap = TimeMap(),
        )

        assertEquals(listOf(100_000L, 300_000L), mapped.map { it.offsetUs })
    }

    @Test
    fun constantSpeedMapsSourceOffsetsToPresentationTimeWithinMicrosecondQuantization() {
        val fast = ObjectTrackingTimelineMapper.toPresentationSamples(
            samples = listOf(ObjectTrackingSourceSample(200_000L, 0.01, 0.02, 0.9)),
            sourceRangeUs = 1_000_000L,
            timeMap = TimeMap(baseSpeed = 2.0),
        )
        val slow = ObjectTrackingTimelineMapper.toPresentationSamples(
            samples = listOf(ObjectTrackingSourceSample(200_000L, 0.01, 0.02, 0.9)),
            sourceRangeUs = 1_000_000L,
            timeMap = TimeMap(baseSpeed = 0.5),
        )

        // TimeMapEvaluator converts continuous speed integration back to integer microseconds.
        // The inverse lower-bound search may therefore land one microsecond before the exact
        // mathematical timestamp when the forward value rounds to the same source microsecond.
        assertTrue(abs(fast.single().offsetUs - 100_000L) <= 1L)
        assertTrue(abs(slow.single().offsetUs - 400_000L) <= 1L)
    }

    @Test
    fun curveUsesSameIntegrationAsRenderTimeMap() {
        val map = TimeMap(
            baseSpeed = 1.0,
            speedPoints = listOf(
                SpeedPoint(0L, 1.0),
                SpeedPoint(1_000_000L, 2.0),
            ),
        )

        val mapped = ObjectTrackingTimelineMapper.toPresentationSamples(
            samples = listOf(ObjectTrackingSourceSample(625_000L, 0.01, 0.02, 0.9)),
            sourceRangeUs = 2_000_000L,
            timeMap = map,
        )

        // Integral from 0 to 500 ms with a linear 1.0 -> 1.5 speed ramp is 625 ms of source.
        assertEquals(500_000L, mapped.single().offsetUs)
    }

    @Test
    fun reverseAndFreezeFailClosedUntilBidirectionalTrackingExists() {
        val sample = listOf(ObjectTrackingSourceSample(100_000L, 0.01, 0.02, 0.9))

        val reverse = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingTimelineMapper.toPresentationSamples(
                samples = sample,
                sourceRangeUs = 1_000_000L,
                timeMap = TimeMap(reverse = true),
            )
        }
        assertEquals("OBJECT_TRACKING_REVERSE_UNSUPPORTED", reverse.message)

        val freeze = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingTimelineMapper.toPresentationSamples(
                samples = sample,
                sourceRangeUs = 1_000_000L,
                timeMap = TimeMap(
                    freezeSegments = listOf(FreezeSegment(100_000L, 200_000L, 100_000L)),
                ),
            )
        }
        assertEquals("OBJECT_TRACKING_FREEZE_UNSUPPORTED", freeze.message)
    }

    @Test
    fun rejectsUnorderedOrOutOfRangeSourceSamples() {
        val unordered = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingTimelineMapper.toPresentationSamples(
                samples = listOf(
                    ObjectTrackingSourceSample(200_000L, 0.01, 0.02, 0.9),
                    ObjectTrackingSourceSample(100_000L, 0.02, 0.03, 0.9),
                ),
                sourceRangeUs = 1_000_000L,
                timeMap = TimeMap(),
            )
        }
        assertEquals("OBJECT_TRACKING_SOURCE_SAMPLES_UNORDERED", unordered.message)

        val outOfRange = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingTimelineMapper.toPresentationSamples(
                samples = listOf(ObjectTrackingSourceSample(1_100_000L, 0.01, 0.02, 0.9)),
                sourceRangeUs = 1_000_000L,
                timeMap = TimeMap(),
            )
        }
        assertEquals("OBJECT_TRACKING_SOURCE_SAMPLE_OUT_OF_RANGE", outOfRange.message)
    }
}
