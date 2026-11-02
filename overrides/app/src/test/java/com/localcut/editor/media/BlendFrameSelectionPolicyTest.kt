package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BlendFrameSelectionPolicyTest {
    @Test
    fun waitsForFutureSecondaryFrameWhenStreamHasNotEnded() {
        assertNull(
            BlendFrameSelectionPolicy.selectClosest(
                primaryTimeUs = 100L,
                secondaryTimesUs = listOf(90L),
                secondaryEnded = false,
            ),
        )
        assertNull(
            BlendFrameSelectionPolicy.selectClosest(
                primaryTimeUs = 100L,
                secondaryTimesUs = listOf(80L, 90L),
                secondaryEnded = false,
            ),
        )
    }

    @Test
    fun choosesClosestAroundPrimaryAndTiePrefersEarlierTimestamp() {
        assertEquals(
            90L,
            BlendFrameSelectionPolicy.selectClosest(100L, listOf(90L, 110L), secondaryEnded = false),
        )
        assertEquals(
            95L,
            BlendFrameSelectionPolicy.selectClosest(100L, listOf(95L, 105L), secondaryEnded = false),
        )
        assertEquals(
            102L,
            BlendFrameSelectionPolicy.selectClosest(100L, listOf(80L, 102L, 130L), secondaryEnded = false),
        )
    }

    @Test
    fun endedStreamCanUseItsLastAvailableFrame() {
        assertEquals(
            90L,
            BlendFrameSelectionPolicy.selectClosest(100L, listOf(70L, 90L), secondaryEnded = true),
        )
        assertEquals(
            130L,
            BlendFrameSelectionPolicy.selectClosest(100L, listOf(130L), secondaryEnded = true),
        )
    }

    @Test
    fun emptyOrInvalidTimelineFailsClosed() {
        assertNull(BlendFrameSelectionPolicy.selectClosest(100L, emptyList(), secondaryEnded = true))
        assertThrows(IllegalArgumentException::class.java) {
            BlendFrameSelectionPolicy.selectClosest(100L, listOf(100L, 90L), secondaryEnded = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlendFrameSelectionPolicy.selectClosest(-1L, listOf(0L, 1L), secondaryEnded = true)
        }
    }
}
