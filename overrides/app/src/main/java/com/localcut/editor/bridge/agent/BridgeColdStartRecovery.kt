package com.localcut.editor.bridge.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import com.localcut.editor.bridge.security.AndroidKeystoreBridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeCredentialStore

internal object BridgeColdStartRecoveryPolicy {
    fun shouldReconnect(
        paired: Boolean,
        alwaysConnected: Boolean,
    ): Boolean = paired && alwaysConnected
}

class BridgeColdStartRecovery(
    context: Context,
    private val settingsStore: BridgeSettingsStore = SharedPreferencesBridgeSettingsStore(context),
    private val credentialStore: BridgeCredentialStore = AndroidKeystoreBridgeCredentialStore(context),
) {
    private val appContext = context.applicationContext

    fun reconnectIfNeeded(): Boolean {
        val shouldReconnect = BridgeColdStartRecoveryPolicy.shouldReconnect(
            paired = credentialStore.load() != null,
            alwaysConnected = settingsStore.alwaysConnected,
        )
        if (!shouldReconnect) return false

        settingsStore.sessionEnabled = true
        val intent = Intent(appContext, BridgeConnectionService::class.java)
            .setAction(BridgeConnectionService.ACTION_RECONNECT)

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrDefault(false)
    }
}
