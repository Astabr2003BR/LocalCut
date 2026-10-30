package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BlendOutputLeasePolicyTest {
    @Test
    fun capacityBlocksUntilReleasedAndReleaseIsOrderedByTimestamp() {
        val policy = BlendOutputLeasePolicy(capacity = 2)

        assertTrue(policy.canAcquire())
        policy.onAcquired(100L)
        policy.onAcquired(200L)
        assertFalse(policy.canAcquire())

        assertEquals(listOf(100L), policy.releaseThrough(150L))
        assertTrue(policy.canAcquire())
        assertEquals(listOf(200L), policy.releaseThrough(200L))
        assertTrue(policy.isEmpty())
    }

    @Test
    fun outputTimestampsMustBeStrictlyIncreasing() {
        val policy = BlendOutputLeasePolicy(2)
        policy.onAcquired(100L)

        assertThrows(IllegalArgumentException::class.java) { policy.onAcquired(100L) }
        assertThrows(IllegalArgumentException::class.java) { policy.onAcquired(99L) }
    }

    @Test
    fun invalidCapacityAndReleaseTimeFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { BlendOutputLeasePolicy(0) }
        val policy = BlendOutputLeasePolicy(1)
        assertThrows(IllegalArgumentException::class.java) { policy.releaseThrough(-1L) }
    }
}
