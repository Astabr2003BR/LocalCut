package com.localcut.editor.domain.time

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.TimeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseClipReplacementPlannerTest {
    @Test
    fun mapsTrimmedSourceRangeIntoWholeFileReverse() {
        val original = Clip(
            id = "clip",
            assetId = "original",
            startUs = 5_000_000L,
            durationUs = 5_000_000L,
            sourceInUs = 2_000_000L,
            sourceOutUs = 7_000_000L,
        )

        val replacement = ReverseClipReplacementPlanner.replace(
            clip = original,
            replacementAssetId = "reversed",
            originalSourceDurationUs = 10_000_000L,
            replacementSourceDurationUs = 10_000_000L,
        )

        assertEquals("reversed", replacement.assetId)
        assertEquals(3_000_000L, replacement.sourceInUs)
        assertEquals(8_000_000L, replacement.sourceOutUs)
        assertEquals(original.startUs, replacement.startUs)
        assertEquals(original.durationUs, replacement.durationUs)
        assertEquals(original.transform, replacement.transform)
        assertEquals(original.effects, replacement.effects)
        assertEquals(original.propertyAnimations, replacement.propertyAnimations)
        assertEquals(original.timeMap, replacement.timeMap)
    }

    @Test
    fun fullSourceRangeRemainsFullRange() {
        val original = Clip("clip", "original", 0L, 10_000_000L, 0L, 10_000_000L)

        val replacement = ReverseClipReplacementPlanner.replace(
            original,
            "reversed",
            10_000_000L,
            10_000_000L,
        )

        assertEquals(0L, replacement.sourceInUs)
        assertEquals(10_000_000L, replacement.sourceOutUs)
    }

    @Test
    fun rejectsIncompatibleDurationsAndNestedReverseOrFreeze() {
        val trimmed = Clip("clip", "original", 0L, 5_000_000L, 2_000_000L, 7_000_000L)
        assertTrue(runCatching {
            ReverseClipReplacementPlanner.replace(trimmed, "reversed", 10_000_000L, 7_500_000L)
        }.isFailure)

        val alreadyReversed = trimmed.copy(timeMap = TimeMap(reverse = true))
        assertTrue(runCatching {
            ReverseClipReplacementPlanner.replace(alreadyReversed, "reversed", 10_000_000L, 10_000_000L)
        }.isFailure)

        val frozen = trimmed.copy(
            timeMap = TimeMap(
                freezeSegments = listOf(FreezeSegment(1_000_000L, 500_000L, 3_000_000L)),
            ),
        )
        assertTrue(runCatching {
            ReverseClipReplacementPlanner.replace(frozen, "reversed", 10_000_000L, 10_000_000L)
        }.isFailure)
    }
}
