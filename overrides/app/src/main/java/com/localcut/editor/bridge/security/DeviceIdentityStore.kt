package com.localcut.editor.bridge.security

import java.security.MessageDigest
import java.util.Base64

interface DeviceIdentityStore {
    fun getOrCreatePublicIdentity(): DevicePublicIdentity
    fun signChallenge(challenge: ByteArray): ByteArray
}

data class DevicePublicIdentity(
    val deviceId: String,
    val algorithm: String,
    val publicKeySpkiBase64: String,
) {
    companion object {
        fun fromPublicKey(publicKeySpki: ByteArray): DevicePublicIdentity {
            require(publicKeySpki.isNotEmpty()) { "public key must not be empty" }
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKeySpki)
            val opaqueId = digest.take(16).joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
            return DevicePublicIdentity(
                deviceId = "lc_$opaqueId",
                algorithm = "EC_P256",
                publicKeySpkiBase64 = Base64.getEncoder().encodeToString(publicKeySpki),
            )
        }
    }
}
