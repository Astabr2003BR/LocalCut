package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.DeviceIdentityStore

fun interface BridgeSessionProvider {
    fun currentSession(): BridgeSession?
}

class StoredBridgeSessionProvider(
    private val relayUrl: String,
    private val identityStore: DeviceIdentityStore,
    private val credentialStore: BridgeCredentialStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : BridgeSessionProvider {
    init {
        require(relayUrl.startsWith("wss://")) { "relayUrl must use wss://" }
    }

    override fun currentSession(): BridgeSession? {
        val credential = credentialStore.load() ?: return null
        if (credential.expiresAtEpochMs <= nowEpochMs()) return null
        val identity = identityStore.getOrCreatePublicIdentity()
        return BridgeSession(
            relayUrl = relayUrl,
            deviceId = identity.deviceId,
            accessToken = credential.accessToken,
        )
    }
}
