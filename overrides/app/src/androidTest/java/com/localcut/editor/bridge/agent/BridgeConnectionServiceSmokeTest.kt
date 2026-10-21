package com.localcut.editor.bridge.agent

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
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

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50L)
        }
        assertTrue("condition was not met within ${timeoutMs}ms", condition())
    }
}
