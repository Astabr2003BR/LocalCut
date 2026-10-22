package com.localcut.editor.bridge.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreBridgeCredentialStore(context: Context) : BridgeCredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun save(credential: BridgeStoredCredential) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(encode(credential))
        val committed = preferences.edit()
            .putString(KEY_IV, Base64.getEncoder().encodeToString(cipher.iv))
            .putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(encrypted))
            .commit()
        check(committed) { "failed to persist encrypted bridge credential" }
    }

    override fun load(): BridgeStoredCredential? {
        val ivEncoded = preferences.getString(KEY_IV, null)
        val ciphertextEncoded = preferences.getString(KEY_CIPHERTEXT, null)
        if (ivEncoded == null || ciphertextEncoded == null) {
            if (ivEncoded != null || ciphertextEncoded != null) clear()
            return null
        }
        return runCatching {
            val iv = Base64.getDecoder().decode(ivEncoded)
            val ciphertext = Base64.getDecoder().decode(ciphertextEncoded)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            decode(cipher.doFinal(ciphertext))
        }.getOrElse {
            clear()
            null
        }
    }

    override fun clear() {
        preferences.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).commit()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(CREDENTIAL_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            CREDENTIAL_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encode(credential: BridgeStoredCredential): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeSizedUtf8(credential.accessToken)
            data.writeBoolean(credential.refreshToken != null)
            credential.refreshToken?.let { refreshToken -> data.writeSizedUtf8(refreshToken) }
            data.writeLong(credential.expiresAtEpochMs)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): BridgeStoredCredential = DataInputStream(ByteArrayInputStream(bytes)).use { data ->
        val accessToken = data.readSizedUtf8()
        val refreshToken = if (data.readBoolean()) data.readSizedUtf8() else null
        val expiresAtEpochMs = data.readLong()
        require(data.available() == 0) { "unexpected credential payload data" }
        BridgeStoredCredential(accessToken, refreshToken, expiresAtEpochMs)
    }

    private fun DataOutputStream.writeSizedUtf8(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FIELD_BYTES) { "credential field too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedUtf8(): String {
        val size = readInt()
        require(size in 0..MAX_FIELD_BYTES) { "invalid credential field size" }
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    companion object {
        const val CREDENTIAL_KEY_ALIAS = "localcut.bridge.credential.v1"
        const val PREFERENCES_NAME = "localcut.bridge.credentials.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val MAX_FIELD_BYTES = 1_048_576
    }
}
