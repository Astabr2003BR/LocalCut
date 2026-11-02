package com.localcut.editor.bridge.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.security.DevicePublicIdentity
import com.localcut.editor.bridge.security.InMemoryBridgeCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BridgeConnectionServiceSmokeTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanup() {
        val settings = SharedPreferencesBridgeSettingsStore(context)
        settings.sessionEnabled = false
        settings.alwaysConnected = false
        runCatching {
            context.startService(
                Intent(context, BridgeConnectionService::class.java)
                    .setAction(BridgeConnectionService.ACTION_DISCONNECT),
            )
        }
    }

    @Test
    fun missingRelayPreferenceFallsBackToOfficialRelayWithoutOverwritingCustomRelay() {
        val preferences = context.getSharedPreferences(
            SharedPreferencesBridgeSettingsStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        check(preferences.edit().clear().commit())

        try {
            val settings = SharedPreferencesBridgeSettingsStore(context)
            settings.alwaysConnected = true

            assertEquals("https://localcut-bridge-v1.onrender.com", settings.relayBaseUrl)
            assertTrue(settings.alwaysConnected)

            settings.relayBaseUrl = "https://bridge.example.com"
            assertEquals("https://bridge.example.com", settings.relayBaseUrl)
            assertTrue(settings.alwaysConnected)
        } finally {
            check(preferences.edit().clear().commit())
        }
    }

    @Test
    fun connectAndManualDisconnectPersistModeBWithoutErasingModeCPreference() {
        val settings = SharedPreferencesBridgeSettingsStore(context)
        settings.sessionEnabled = false
        settings.alwaysConnected = true

        val connect = Intent(context, BridgeConnectionService::class.java)
            .setAction(BridgeConnectionService.ACTION_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(connect)
        } else {
            context.startService(connect)
        }

        waitUntil { settings.sessionEnabled }
        assertTrue(settings.alwaysConnected)

        context.startService(
            Intent(context, BridgeConnectionService::class.java)
                .setAction(BridgeConnectionService.ACTION_DISCONNECT),
        )

        waitUntil { !settings.sessionEnabled }
        assertFalse(settings.sessionEnabled)
        assertTrue(settings.alwaysConnected)
    }

    @Test
    fun expiredCredentialIsPreservedWhenRenewalTransportIsTemporarilyUnavailable() {
        val credentials = InMemoryBridgeCredentialStore().apply {
            save(
                BridgeStoredCredential(
                    accessToken = "expired-access",
                    refreshToken = "expired-refresh",
                    expiresAtEpochMs = 1L,
                    recoveryToken = "recovery-token",
                ),
            )
        }
        val settings = InMemoryBridgeSettingsStore(
            sessionEnabled = true,
            alwaysConnected = true,
            relayBaseUrl = "https://127.0.0.1:1",
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val control = LocalCutBridgeControl(
            context = context,
            scope = scope,
            settingsStore = settings,
            credentialStore = credentials,
            identityStore = testIdentity(),
            nowEpochMs = { 10_000L },
        )

        try {
            assertTrue(control.state.value.paired)
            control.connect()

            waitUntil(timeoutMs = 10_000L) {
                control.state.value.phase == BridgeUiPhase.ERROR
            }

            assertNotNull("transient renewal failure must preserve local credential", credentials.load())
            assertTrue("transient renewal failure must preserve paired UI state", control.state.value.paired)
            assertTrue("always-connected preference must survive transient renewal failure", control.state.value.alwaysConnected)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unauthorizedSocketPreservesCredentialWhileRefreshTransportIsUnavailable() {
        val credentials = InMemoryBridgeCredentialStore().apply {
            save(
                BridgeStoredCredential(
                    accessToken = "server-rejected-access",
                    refreshToken = "refresh-token",
                    expiresAtEpochMs = Long.MAX_VALUE,
                    recoveryToken = "recovery-token",
                ),
            )
        }
        val settings = InMemoryBridgeSettingsStore(
            sessionEnabled = true,
            alwaysConnected = true,
            relayBaseUrl = "https://127.0.0.1:1",
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val control = LocalCutBridgeControl(
            context = context,
            scope = scope,
            settingsStore = settings,
            credentialStore = credentials,
            identityStore = testIdentity(),
            nowEpochMs = { 10_000L },
        )

        try {
            assertTrue(control.state.value.paired)
            control.connectionObserver.onDisconnected(401, "Unauthorized")

            waitUntil(timeoutMs = 10_000L) {
                control.state.value.phase == BridgeUiPhase.ERROR
            }

            assertNotNull("temporary refresh transport failure must not erase paired credential", credentials.load())
            assertTrue("temporary refresh transport failure must keep paired UI state", control.state.value.paired)
            assertTrue("always-connected preference must survive auth recovery", control.state.value.alwaysConnected)
        } finally {
            scope.cancel()
        }
    }

    private fun testIdentity(): DeviceIdentityStore = object : DeviceIdentityStore {
        override fun getOrCreatePublicIdentity(): DevicePublicIdentity = DevicePublicIdentity(
            deviceId = "lc_test_device",
            algorithm = "EC_P256",
            publicKeySpkiBase64 = "AQ==",
        )

        override fun signChallenge(challenge: ByteArray): ByteArray = byteArrayOf(1)
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50L)
        }
        assertTrue("condition was not met within ${timeoutMs}ms", condition())
    }
}
