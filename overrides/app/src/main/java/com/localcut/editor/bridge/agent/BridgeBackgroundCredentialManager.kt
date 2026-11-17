package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgePairingClient

class BridgeBackgroundCredentialManager(
    private val credentialStore: BridgeCredentialStore,
    private val client: BridgePairingClient,
    private val identityStore: DeviceIdentityStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun ensureFresh(): Result<Boolean> = runCatching {
        val current = credentialStore.load() ?: return@runCatching false
        if (current.expiresAtEpochMs > nowEpochMs()) return@runCatching true

        val renewed = BridgeCredentialRenewal(
            client = client,
            identityStore = identityStore,
            nowEpochMs = nowEpochMs,
        ).renew(current).getOrThrow()

        if (renewed == null) {
            credentialStore.clear()
            false
        } else {
            credentialStore.save(renewed)
            true
        }
    }
}
