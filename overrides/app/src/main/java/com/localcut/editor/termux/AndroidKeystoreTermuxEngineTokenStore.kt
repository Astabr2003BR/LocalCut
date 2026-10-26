package com.localcut.editor.termux

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreTermuxEngineTokenStore(context: Context) : TermuxEngineTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun save(token: String) {
        val normalized = TermuxEngineTokenPolicy.normalize(token)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        check(
            preferences.edit()
                .putString(KEY_IV, Base64.getEncoder().encodeToString(cipher.iv))
                .putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
                .commit(),
        ) { "TERMUX_ENGINE_TOKEN_PERSIST_FAILED" }
    }

    override fun load(): String? {
        val ivEncoded = preferences.getString(KEY_IV, null)
        val ciphertextEncoded = preferences.getString(KEY_CIPHERTEXT, null)
        if (ivEncoded == null || ciphertextEncoded == null) {
            if (ivEncoded != null || ciphertextEncoded != null) clear()
            return null
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(ivEncoded)),
            )
            cipher.updateAAD(AAD)
            val raw = cipher.doFinal(Base64.getDecoder().decode(ciphertextEncoded)).toString(Charsets.UTF_8)
            TermuxEngineTokenPolicy.normalize(raw)
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
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "localcut.termux.engine.token.v1"
        const val PREFERENCES_NAME = "localcut.termux.engine.credentials.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        val AAD = "localcut-termux-engine-v1".toByteArray(Charsets.UTF_8)
    }
}
