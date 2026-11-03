package com.localcut.editor.bridge.agent

internal data class BridgeTaskRemovalDecision(
    val preserveConnection: Boolean,
    val reconnectImmediately: Boolean,
)

internal object BridgeTaskRemovalPolicy {
    fun decide(
        sessionEnabled: Boolean,
        alwaysConnected: Boolean,
    ): BridgeTaskRemovalDecision {
        val preserveConnection = sessionEnabled && alwaysConnected
        return BridgeTaskRemovalDecision(
            preserveConnection = preserveConnection,
            // onTaskRemoved() is invoked while the foreground service may still own a live socket.
            // Starting another connection here races with that socket and can fail with
            // "bridge socket already active". A real service/process restart is handled by
            // START_STICKY / cold-start recovery instead.
            reconnectImmediately = false,
        )
    }

    fun shouldPreserveConnection(
        sessionEnabled: Boolean,
        alwaysConnected: Boolean,
    ): Boolean = decide(
        sessionEnabled = sessionEnabled,
        alwaysConnected = alwaysConnected,
    ).preserveConnection
}
