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

internal data class BridgeDisconnectDecision(
    val honorDisconnect: Boolean,
    val disableSession: Boolean,
)

/**
 * Keeps the user's desired Bridge session separate from transport/process lifetime.
 *
 * Automatic Android/OEM teardown, sticky restarts and stale service intents must never
 * become a manual disconnect. Only an explicit user action may disable the persisted
 * session intent.
 */
internal object BridgeSessionPersistencePolicy {
    fun disconnectDecision(
        sessionEnabled: Boolean,
        explicitUserRequest: Boolean,
    ): BridgeDisconnectDecision = when {
        explicitUserRequest -> BridgeDisconnectDecision(
            honorDisconnect = true,
            disableSession = true,
        )
        !sessionEnabled -> BridgeDisconnectDecision(
            // The UI/revoke path persisted the manual choice before asking the service
            // to tear down transport, so finishing that shutdown is safe.
            honorDisconnect = true,
            disableSession = false,
        )
        else -> BridgeDisconnectDecision(
            // Session is still desired. Treat this as stale/re-delivered and keep it alive.
            honorDisconnect = false,
            disableSession = false,
        )
    }

    fun disableSessionOnAutomaticCredentialLoss(): Boolean = false
}
