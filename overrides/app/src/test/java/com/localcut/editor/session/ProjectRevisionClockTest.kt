package com.localcut.editor.session

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectRevisionClockTest {
    @Test
    fun revisionsAreIndependentPerProjectAndMonotonic() {
        val clock = InMemoryProjectRevisionClock()

        assertEquals(0L, clock.current("project-a"))
        assertEquals(0L, clock.current("project-b"))

        assertEquals(1L, clock.next("project-a"))
        assertEquals(2L, clock.next("project-a"))
        assertEquals(1L, clock.next("project-b"))

        assertEquals(2L, clock.current("project-a"))
        assertEquals(1L, clock.current("project-b"))
    }

    @Test
    fun currentNeverMutatesOrDecrementsRevision() {
        val clock = InMemoryProjectRevisionClock(mapOf("project-a" to 7L))

        assertEquals(7L, clock.current("project-a"))
        assertEquals(7L, clock.current("project-a"))
        assertEquals(8L, clock.next("project-a"))
        assertEquals(8L, clock.current("project-a"))
    }
}
