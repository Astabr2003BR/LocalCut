package com.localcut.editor.bridge.security

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreDeviceIdentitySmokeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clean() {
        deleteAlias(AndroidKeystoreDeviceIdentityStore.DEVICE_KEY_ALIAS)
        deleteAlias(AndroidKeystoreBridgeCredentialStore.CREDENTIAL_KEY_ALIAS)
        context.getSharedPreferences(AndroidKeystoreBridgeCredentialStore.PREFERENCES_NAME, 0).edit().clear().commit()
    }

    @After
    fun cleanup() = clean()

    @Test
    fun p256IdentityIsStableAndSignatureVerifiesWithExportedPublicKey() {
        val store = AndroidKeystoreDeviceIdentityStore()
        val first = store.getOrCreatePublicIdentity()
        val second = store.getOrCreatePublicIdentity()
        assertEquals(first, second)
        assertEquals("EC_P256", first.algorithm)

        val publicBytes = Base64.decode(first.publicKeySpkiBase64, Base64.NO_WRAP)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicBytes))
        val challenge = "localcut-bridge-challenge".toByteArray()
        val signatureBytes = store.signChallenge(challenge)

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(challenge)
        assertTrue(verifier.verify(signatureBytes))
    }

    @Test
    fun credentialStoreEncryptsAtRestAndRoundTripsThenClears() {
        val store: BridgeCredentialStore = AndroidKeystoreBridgeCredentialStore(context)
        val credential = BridgeStoredCredential(
            accessToken = "access-super-secret",
            refreshToken = "refresh-super-secret",
            expiresAtEpochMs = 987_654L,
        )

        store.save(credential)
        assertEquals(credential, store.load())

        val prefs = context.getSharedPreferences(AndroidKeystoreBridgeCredentialStore.PREFERENCES_NAME, 0)
        val stored = prefs.all.values.joinToString("|")
        assertTrue(stored.isNotBlank())
        assertTrue(!stored.contains("access-super-secret"))
        assertTrue(!stored.contains("refresh-super-secret"))

        store.clear()
        assertNull(store.load())
    }

    private fun deleteAlias(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}
