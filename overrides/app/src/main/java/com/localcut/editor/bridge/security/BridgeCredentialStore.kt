package com.localcut.editor.bridge.security

data class BridgeStoredCredential(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
    val recoveryToken: String? = null,
) {
    init {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        require(refreshToken == null || refreshToken.isNotBlank()) { "refreshToken must not be blank" }
        require(recoveryToken == null || recoveryToken.isNotBlank()) { "recoveryToken must not be blank" }
        require(expiresAtEpochMs >= 0L) { "expiresAtEpochMs must be non-negative" }
    }

    override fun toString(): String =
        "BridgeStoredCredential(accessToken=<redacted>, refreshToken=<redacted>, expiresAtEpochMs=$expiresAtEpochMs, recoveryToken=<redacted>)"
}

interface BridgeCredentialStore {
    fun save(credential: BridgeStoredCredential)
    fun load(): BridgeStoredCredential?
    fun clear()
}

class InMemoryBridgeCredentialStore : BridgeCredentialStore {
    private var value: BridgeStoredCredential? = null

    override fun save(credential: BridgeStoredCredential) {
        value = credential
    }

    override fun load(): BridgeStoredCredential? = value

    override fun clear() {
        value = null
    }
}
