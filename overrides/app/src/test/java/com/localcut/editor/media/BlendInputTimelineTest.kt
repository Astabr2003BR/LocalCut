package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BlendInputTimelineTest {
    @Test
    fun waitsUntilSecondaryCanBracketPrimaryThenSelectsClosest() {
        val timeline = BlendInputTimeline<String>()
        timeline.registerInput(0)
        timeline.registerInput(1)

        assertTrue(timeline.queue(0, 100L, "p100").isEmpty())
        assertTrue(timeline.queue(1, 90L, "s90").isEmpty())
        assertNull(timeline.selectForNextOutput())
        assertTrue(timeline.queue(1, 110L, "s110").isEmpty())

        val selected = timeline.selectForNextOutput()!!
        assertEquals(listOf("p100", "s90"), selected.map { it.payload })
    }

    @Test
    fun consumingPrimaryReleasesItAndPrunesObsoleteSecondaryFrames() {
        val timeline = BlendInputTimeline<String>()
        timeline.registerInput(0)
        timeline.registerInput(1)
        timeline.queue(0, 100L, "p100")
        timeline.queue(0, 200L, "p200")
        timeline.queue(1, 80L, "s80")
        timeline.queue(1, 90L, "s90")
        timeline.queue(1, 210L, "s210")

        val released = timeline.consumePrimaryAndPrune()

        assertEquals(listOf("p100", "s80"), released.map { it.payload })
        assertEquals(listOf("p200", "s90"), timeline.selectForNextOutput()!!.map { it.payload })
    }

    @Test
    fun endedSecondaryCanUseLastFrameAndEndedPrimaryDrainsSecondary() {
        val timeline = BlendInputTimeline<String>()
        timeline.registerInput(0)
        timeline.registerInput(1)
        timeline.queue(0, 100L, "p100")
        timeline.queue(1, 90L, "s90")
        timeline.endInput(1)

        assertEquals(listOf("p100", "s90"), timeline.selectForNextOutput()!!.map { it.payload })
        timeline.endInput(0)
        assertEquals(listOf("p100", "s90"), timeline.consumePrimaryAndPrune().map { it.payload })
        assertTrue(timeline.allEndedAndPrimaryEmpty())
    }

    @Test
    fun idsAndTimestampsMustBeContiguousAndStrictlyIncreasing() {
        val timeline = BlendInputTimeline<String>()
        assertThrows(IllegalArgumentException::class.java) { timeline.registerInput(1) }
        timeline.registerInput(0)
        timeline.registerInput(1)
        timeline.queue(0, 100L, "p100")
        assertThrows(IllegalArgumentException::class.java) { timeline.queue(0, 100L, "dup") }
        assertThrows(IllegalArgumentException::class.java) { timeline.queue(0, 99L, "back") }
        assertFalse(timeline.allEndedAndPrimaryEmpty())
    }
}
