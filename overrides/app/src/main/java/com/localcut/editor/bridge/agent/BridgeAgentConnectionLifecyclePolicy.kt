package com.localcut.editor.bridge.agent

data class BridgeAgentServiceTransition(
    val action: String,
    val foreground: Boolean,
)

object BridgeAgentConnectionLifecyclePolicy {
    fun beforeConnectAttempt(): BridgeAgentServiceTransition =
        BridgeAgentServiceTransition(
            action = BridgeConnectionService.ACTION_PREPARE_CONNECT,
            foreground = true,
        )

    fun afterConnectAttempt(
        sessionEnabled: Boolean,
        credentialPresent: Boolean,
    ): BridgeAgentServiceTransition? =
        if (!sessionEnabled && !credentialPresent) {
            BridgeAgentServiceTransition(
                action = BridgeConnectionService.ACTION_STOP_PREPARE_CONNECT,
                foreground = false,
            )
        } else {
            null
        }
}
