package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.security.BridgeMobilePairingProof
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

data class BridgeMobilePairingClaim(
    val deviceId: String,
    val challengeBase64: String,
    val expiresAtEpochMs: Long,
)

data class BridgeSessionRecoveryProof(
    val deviceId: String,
    val publicKeySpkiBase64: String,
    val recoveryToken: String,
    val issuedAtEpochMs: Long,
    val nonceBase64: String,
    val signatureBase64: String,
)

interface BridgePairingClient {
    fun start(identity: DevicePublicIdentity): Result<BridgePairingStart>
    fun status(code: String, signatureBase64: String? = null): Result<BridgePairingStatus>
    fun refresh(refreshToken: String): Result<BridgeStoredCredential>
    fun recover(proof: BridgeSessionRecoveryProof): Result<BridgeStoredCredential> =
        Result.failure(UnsupportedOperationException("Bridge session recovery is not implemented"))
    fun mobileClaim(proof: BridgeMobilePairingProof): Result<BridgeMobilePairingClaim> =
        Result.failure(UnsupportedOperationException("Bridge mobile pairing claim is not implemented"))
    fun unpair(accessToken: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Bridge self-unpair is not implemented"))
}
