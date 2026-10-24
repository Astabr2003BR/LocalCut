package com.localcut.editor.bridge.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class AndroidKeystoreDeviceIdentityStore : DeviceIdentityStore {
    override fun getOrCreatePublicIdentity(): DevicePublicIdentity {
        ensureKeyPair()
        val keyStore = loadKeyStore()
        val publicKey = keyStore.getCertificate(DEVICE_KEY_ALIAS)?.publicKey
            ?: error("bridge public key unavailable")
        return DevicePublicIdentity.fromPublicKey(publicKey.encoded)
    }

    override fun signChallenge(challenge: ByteArray): ByteArray {
        require(challenge.isNotEmpty()) { "challenge must not be empty" }
        require(challenge.size <= MAX_CHALLENGE_BYTES) { "challenge is too large" }
        ensureKeyPair()
        val privateKey = loadKeyStore().getKey(DEVICE_KEY_ALIAS, null) as? PrivateKey
            ?: error("bridge private key unavailable")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(challenge)
            sign()
        }
    }

    private fun ensureKeyPair() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(DEVICE_KEY_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            DEVICE_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val DEVICE_KEY_ALIAS = "localcut.bridge.device.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val MAX_CHALLENGE_BYTES = 4096
    }
}
