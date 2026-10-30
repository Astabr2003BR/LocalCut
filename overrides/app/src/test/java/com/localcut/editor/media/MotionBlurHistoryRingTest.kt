package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionBlurHistoryRingTest {
    @Test
    fun ringExposesNewestHistoryFirstAndOverwritesOldestSlot() {
        val ring = MotionBlurHistoryRing(capacity = 3)

        assertEquals(emptyList<Int>(), ring.newestFirstIndices())
        assertEquals(0, ring.nextWriteIndex())

        ring.commitWrite()
        assertEquals(listOf(0), ring.newestFirstIndices())
        assertEquals(1, ring.nextWriteIndex())

        ring.commitWrite()
        assertEquals(listOf(1, 0), ring.newestFirstIndices())
        assertEquals(2, ring.nextWriteIndex())

        ring.commitWrite()
        assertEquals(listOf(2, 1, 0), ring.newestFirstIndices())
        assertEquals(0, ring.nextWriteIndex())

        ring.commitWrite()
        assertEquals(listOf(0, 2, 1), ring.newestFirstIndices())
        assertEquals(1, ring.nextWriteIndex())
    }

    @Test
    fun resetDropsAllRecordedHistoryWithoutChangingCapacity() {
        val ring = MotionBlurHistoryRing(capacity = 2)
        ring.commitWrite()
        ring.commitWrite()

        ring.reset()

        assertEquals(0, ring.size)
        assertEquals(emptyList<Int>(), ring.newestFirstIndices())
        assertEquals(0, ring.nextWriteIndex())
    }

    @Test
    fun zeroCapacityRingNeverOffersAWriteSlot() {
        val ring = MotionBlurHistoryRing(capacity = 0)

        assertNull(ring.nextWriteIndex())
        ring.commitWrite()
        assertEquals(0, ring.size)
        assertEquals(emptyList<Int>(), ring.newestFirstIndices())
    }
}
