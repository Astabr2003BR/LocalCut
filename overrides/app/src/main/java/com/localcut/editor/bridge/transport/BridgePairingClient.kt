package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DevicePublicIdentity

data class BridgePairingStart(
    val code: String,
    val expiresAtEpochMs: Long,
)

data class BridgePairingStatus(
    val state: String,
    val challengeBase64: String? = null,
    val credential: BridgeStoredCredential? = null,
)

interface BridgePairingClient {
    fun start(identity: DevicePublicIdentity): Result<BridgePairingStart>
    fun status(code: String, signatureBase64: String? = null): Result<BridgePairingStatus>
    fun refresh(refreshToken: String): Result<BridgeStoredCredential>
}
