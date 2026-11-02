package com.localcut.editor.bridge.agent

internal object BridgeTaskRemovalPolicy {
    fun shouldPreserveConnection(
        sessionEnabled: Boolean,
        alwaysConnected: Boolean,
    ): Boolean = sessionEnabled && alwaysConnected
}
