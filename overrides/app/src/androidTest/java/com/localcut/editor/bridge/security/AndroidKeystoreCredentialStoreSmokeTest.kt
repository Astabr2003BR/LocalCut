package com.localcut.editor.bridge.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Base64
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCredentialStoreSmokeTest {
    @Test
    fun corruptedEncryptedCredentialIsDiscardedInsteadOfCrashingBridgeStartup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AndroidKeystoreBridgeCredentialStore(context)
        val preferences = context.getSharedPreferences(
            AndroidKeystoreBridgeCredentialStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        store.clear()
        store.save(
            BridgeStoredCredential(
                accessToken = "access",
                refreshToken = "refresh",
                expiresAtEpochMs = Long.MAX_VALUE,
            ),
        )
        preferences.edit()
            .putString("ciphertext", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
            .commit()

        val loaded = runCatching { store.load() }

        assertTrue("corrupted bridge credential must not crash startup", loaded.isSuccess)
        assertNull("corrupted bridge credential must be discarded", loaded.getOrNull())
        assertTrue("credential preferences must be cleared after corruption", preferences.all.isEmpty())
    }
}
