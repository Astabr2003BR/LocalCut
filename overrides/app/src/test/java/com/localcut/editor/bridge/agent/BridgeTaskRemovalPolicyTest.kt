package com.localcut.editor.bridge.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTaskRemovalPolicyTest {
    @Test
    fun preservesCurrentConnectionWithoutImmediateReconnectWhenEnabled() {
        val decision = BridgeTaskRemovalPolicy.decide(
            sessionEnabled = true,
            alwaysConnected = true,
        )

        assertTrue(decision.preserveConnection)
        assertFalse(decision.reconnectImmediately)
    }

    @Test
    fun doesNotPreserveWhenSessionIsDisabled() {
        val decision = BridgeTaskRemovalPolicy.decide(
            sessionEnabled = false,
            alwaysConnected = true,
        )

        assertFalse(decision.preserveConnection)
        assertFalse(decision.reconnectImmediately)
    }

    @Test
    fun doesNotPreserveWhenAlwaysConnectedIsDisabled() {
        val decision = BridgeTaskRemovalPolicy.decide(
            sessionEnabled = true,
            alwaysConnected = false,
        )

        assertFalse(decision.preserveConnection)
        assertFalse(decision.reconnectImmediately)
    }
}
