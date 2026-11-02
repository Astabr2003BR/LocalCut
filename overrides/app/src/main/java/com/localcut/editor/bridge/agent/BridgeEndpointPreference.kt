package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.transport.BridgeEndpoint
import com.localcut.editor.bridge.transport.BridgeEndpointConfig

class BridgeEndpointPreference(
    private val settingsStore: BridgeSettingsStore,
    private val credentialStore: BridgeCredentialStore,
) {
    fun current(): Result<BridgeEndpoint> = BridgeEndpointConfig.resolve(settingsStore.relayBaseUrl)

    fun save(rawBaseUrl: String): Result<BridgeEndpoint> {
        val endpoint = BridgeEndpointConfig.resolve(rawBaseUrl).getOrElse {
            return Result.failure(it)
        }
        val current = BridgeEndpointConfig.resolve(settingsStore.relayBaseUrl).getOrNull()
        if (credentialStore.load() != null && current?.httpsBaseUrl != endpoint.httpsBaseUrl) {
            return Result.failure(
                IllegalStateException("PAIRING_MUST_BE_REVOKED_BEFORE_ENDPOINT_CHANGE"),
            )
        }
        settingsStore.relayBaseUrl = endpoint.httpsBaseUrl
        return Result.success(endpoint)
    }
}
