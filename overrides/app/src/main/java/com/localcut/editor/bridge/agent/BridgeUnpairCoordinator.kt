package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.transport.BridgePairingClient

class BridgeUnpairCoordinator(
    private val client: BridgePairingClient,
    private val credentialStore: BridgeCredentialStore,
    private val settingsStore: BridgeSettingsStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun unpair(): Result<Unit> = runCatching {
        var credential = credentialStore.load()
            ?: return@runCatching clearLocalState()

        if (credential.expiresAtEpochMs <= nowEpochMs()) {
            credential = refreshRequired(credential)
        }

        val firstAttempt = client.unpair(credential.accessToken)
        if (firstAttempt.isSuccess) {
            return@runCatching clearLocalState()
        }

        val firstError = firstAttempt.exceptionOrNull()
            ?: throw IllegalStateException("BRIDGE_UNPAIR_FAILED")
        if (!firstError.isAuthRequired()) throw firstError

        val refreshToken = credential.refreshToken
            ?: throw IllegalStateException("BRIDGE_REFRESH_TOKEN_MISSING")
        val refreshed = client.refresh(refreshToken).getOrElse { refreshError ->
            if (refreshError.isAuthRequired()) {
                return@runCatching clearLocalState()
            }
            throw refreshError
        }
        credentialStore.save(refreshed)

        client.unpair(refreshed.accessToken).getOrElse { retryError ->
            if (retryError.isAuthRequired()) {
                return@runCatching clearLocalState()
            }
            throw retryError
        }
        clearLocalState()
    }

    private fun refreshRequired(credential: BridgeStoredCredential): BridgeStoredCredential {
        val refreshToken = credential.refreshToken
            ?: throw IllegalStateException("BRIDGE_REFRESH_TOKEN_MISSING")
        return client.refresh(refreshToken).getOrThrow().also(credentialStore::save)
    }

    private fun clearLocalState() {
        credentialStore.clear()
        settingsStore.sessionEnabled = false
        settingsStore.alwaysConnected = false
    }

    private fun Throwable.isAuthRequired(): Boolean = message == "AUTH_REQUIRED"
}
