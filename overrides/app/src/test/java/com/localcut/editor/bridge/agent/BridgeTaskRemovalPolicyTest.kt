package com.localcut.editor.bridge.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTaskRemovalPolicyTest {
    @Test
    fun reconnectsWhenSessionAndAlwaysConnectedAreEnabled() {
        assertTrue(
            BridgeTaskRemovalPolicy.shouldPreserveConnection(
                sessionEnabled = true,
                alwaysConnected = true,
            ),
        )
    }

    @Test
    fun doesNotReconnectWhenSessionIsDisabled() {
        assertFalse(
            BridgeTaskRemovalPolicy.shouldPreserveConnection(
                sessionEnabled = false,
                alwaysConnected = true,
            ),
        )
    }

    @Test
    fun doesNotReconnectWhenAlwaysConnectedIsDisabled() {
        assertFalse(
            BridgeTaskRemovalPolicy.shouldPreserveConnection(
                sessionEnabled = true,
                alwaysConnected = false,
            ),
        )
    }
}
