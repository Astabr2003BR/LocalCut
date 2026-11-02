package com.localcut.editor.bridge.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeReconnectPolicyTest {
    @Test
    fun exponentialBackoffStartsAtOneSecondAndCapsAtSixtySeconds() {
        val policy = BridgeReconnectPolicy(jitterFraction = 0.0)

        assertEquals(1_000L, policy.delayMs(attempt = 0, randomUnit = 0.5))
        assertEquals(2_000L, policy.delayMs(attempt = 1, randomUnit = 0.5))
        assertEquals(4_000L, policy.delayMs(attempt = 2, randomUnit = 0.5))
        assertEquals(60_000L, policy.delayMs(attempt = 20, randomUnit = 0.5))
    }

    @Test
    fun jitterIsBoundedAroundExponentialDelay() {
        val policy = BridgeReconnectPolicy(jitterFraction = 0.20)

        assertEquals(3_200L, policy.delayMs(attempt = 2, randomUnit = 0.0))
        assertEquals(4_000L, policy.delayMs(attempt = 2, randomUnit = 0.5))
        assertEquals(4_800L, policy.delayMs(attempt = 2, randomUnit = 1.0))
        assertTrue(policy.delayMs(attempt = 20, randomUnit = 1.0) <= 60_000L)
    }

    @Test
    fun manualDisconnectSuppressesReconnectUntilUserConnectsAgain() {
        val policy = BridgeReconnectPolicy()

        assertFalse(policy.shouldReconnect(manuallyDisconnected = true, sessionEnabled = true))
        assertTrue(policy.shouldReconnect(manuallyDisconnected = false, sessionEnabled = true))
        assertFalse(policy.shouldReconnect(manuallyDisconnected = false, sessionEnabled = false))
    }
}
