package com.localcut.editor.bridge.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

data class BridgeMobilePairingProof(
    val code: String,
    val issuedAtEpochMs: Long,
    val nonceBase64: String,
    val signatureBase64: String,
)

class BridgeMobilePairingProofFactory(
    private val identityStore: DeviceIdentityStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val nonceGenerator: () -> ByteArray = {
        ByteArray(DEFAULT_NONCE_BYTES).also(SecureRandom()::nextBytes)
    },
) {
    fun create(code: String): BridgeMobilePairingProof {
        require(CODE_REGEX.matches(code)) { "PAIRING_CODE_INVALID" }
        val nonce = nonceGenerator()
        require(nonce.size in MIN_NONCE_BYTES..MAX_NONCE_BYTES) { "PAIRING_NONCE_INVALID" }

        val issuedAtEpochMs = nowEpochMs()
        val nonceBase64 = Base64.getEncoder().encodeToString(nonce)
        val payload = "$CLAIM_PREFIX\n$code\n$issuedAtEpochMs\n$nonceBase64"
            .toByteArray(StandardCharsets.UTF_8)
        val signatureBase64 = Base64.getEncoder().encodeToString(
            identityStore.signChallenge(payload),
        )
        return BridgeMobilePairingProof(
            code = code,
            issuedAtEpochMs = issuedAtEpochMs,
            nonceBase64 = nonceBase64,
            signatureBase64 = signatureBase64,
        )
    }

    private companion object {
        const val CLAIM_PREFIX = "LOCALCUT_MOBILE_CLAIM_V1"
        const val DEFAULT_NONCE_BYTES = 32
        const val MIN_NONCE_BYTES = 16
        const val MAX_NONCE_BYTES = 64
        val CODE_REGEX = Regex("^[0-9]{6}$")
    }
}
