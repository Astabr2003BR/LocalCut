package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgePairingClient
import com.localcut.editor.bridge.transport.BridgeSessionRecoveryProof
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

class BridgeCredentialRenewal(
    private val client: BridgePairingClient,
    private val identityStore: DeviceIdentityStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val nonceGenerator: () -> ByteArray = {
        ByteArray(DEFAULT_NONCE_BYTES).also(SecureRandom()::nextBytes)
    },
) {
    fun renew(current: BridgeStoredCredential): Result<BridgeStoredCredential?> {
        val refreshToken = current.refreshToken
        if (refreshToken != null) {
            val refreshed = client.refresh(refreshToken)
            refreshed.getOrNull()?.let { return Result.success(it) }
            val refreshError = refreshed.exceptionOrNull()
                ?: return Result.failure(IllegalStateException("BRIDGE_REFRESH_FAILED"))
            if (!isRefreshAuthFailure(refreshError)) return Result.failure(refreshError)
        }

        val recoveryToken = current.recoveryToken ?: return Result.success(null)
        val proof = runCatching { createRecoveryProof(recoveryToken) }
            .getOrElse { return Result.failure(it) }
        val recovered = client.recover(proof)
        recovered.getOrNull()?.let { return Result.success(it) }
        val recoveryError = recovered.exceptionOrNull()
            ?: return Result.failure(IllegalStateException("BRIDGE_RECOVERY_FAILED"))
        return if (isTerminalRecoveryFailure(recoveryError)) {
            Result.success(null)
        } else {
            Result.failure(recoveryError)
        }
    }

    private fun createRecoveryProof(recoveryToken: String): BridgeSessionRecoveryProof {
        val identity = identityStore.getOrCreatePublicIdentity()
        val nonce = nonceGenerator()
        require(nonce.size in MIN_NONCE_BYTES..MAX_NONCE_BYTES) { "RECOVERY_NONCE_INVALID" }
        val issuedAtEpochMs = nowEpochMs()
        val nonceBase64 = Base64.getEncoder().encodeToString(nonce)
        val payload = "$RECOVERY_PREFIX\n${identity.deviceId}\n$issuedAtEpochMs\n$nonceBase64\n$recoveryToken"
            .toByteArray(StandardCharsets.UTF_8)
        val signatureBase64 = Base64.getEncoder().encodeToString(identityStore.signChallenge(payload))
        return BridgeSessionRecoveryProof(
            deviceId = identity.deviceId,
            publicKeySpkiBase64 = identity.publicKeySpkiBase64,
            recoveryToken = recoveryToken,
            issuedAtEpochMs = issuedAtEpochMs,
            nonceBase64 = nonceBase64,
            signatureBase64 = signatureBase64,
        )
    }

    private fun isRefreshAuthFailure(error: Throwable): Boolean = when (error.message) {
        "AUTH_REQUIRED", "REFRESH_TOKEN_INVALID" -> true
        else -> false
    }

    private fun isTerminalRecoveryFailure(error: Throwable): Boolean {
        if (error is IOException) return false
        return error.message in TERMINAL_RECOVERY_CODES
    }

    private companion object {
        const val RECOVERY_PREFIX = "LOCALCUT_SESSION_RECOVERY_V1"
        const val DEFAULT_NONCE_BYTES = 32
        const val MIN_NONCE_BYTES = 16
        const val MAX_NONCE_BYTES = 64
        val TERMINAL_RECOVERY_CODES = setOf(
            "AUTH_REQUIRED",
            "RECOVERY_TOKEN_INVALID",
            "RECOVERY_TOKEN_EXPIRED",
            "RECOVERY_SIGNATURE_INVALID",
            "RECOVERY_PROOF_EXPIRED",
            "RECOVERY_REPLAY",
            "DEVICE_IDENTITY_INVALID",
            "DEVICE_IDENTITY_MISMATCH",
            "DEVICE_REVOKED",
        )
    }
}
