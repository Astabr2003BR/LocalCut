package com.localcut.editor.bridge.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeColdStartRecoveryPolicyTest {
    @Test
    fun pairedAlwaysConnectedSessionReconnectsOnColdStart() {
        assertTrue(
            BridgeColdStartRecoveryPolicy.shouldReconnect(
                paired = true,
                alwaysConnected = true,
            ),
        )
    }

    @Test
    fun unpairedSessionDoesNotReconnectOnColdStart() {
        assertFalse(
            BridgeColdStartRecoveryPolicy.shouldReconnect(
                paired = false,
                alwaysConnected = true,
            ),
        )
    }

    @Test
    fun manuallyDisabledAlwaysConnectedDoesNotReconnectOnColdStart() {
        assertFalse(
            BridgeColdStartRecoveryPolicy.shouldReconnect(
                paired = true,
                alwaysConnected = false,
            ),
        )
    }
}
