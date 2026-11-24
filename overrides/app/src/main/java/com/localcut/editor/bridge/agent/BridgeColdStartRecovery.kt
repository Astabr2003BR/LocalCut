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
        sessionEnabled: Boolean,
    ): Boolean = paired && alwaysConnected && sessionEnabled

    fun attemptRecovery(
        paired: Boolean,
        settingsStore: BridgeSettingsStore,
        startReconnect: () -> Boolean,
    ): Boolean {
        if (!shouldReconnect(paired, settingsStore.alwaysConnected, settingsStore.sessionEnabled)) {
            return false
        }
        // Recovery must never enable a session: only an explicit connect may do that.
        return startReconnect()
    }
}

class BridgeColdStartRecovery(
    context: Context,
    private val settingsStore: BridgeSettingsStore = SharedPreferencesBridgeSettingsStore(context),
    private val credentialStore: BridgeCredentialStore = AndroidKeystoreBridgeCredentialStore(context),
) {
    private val appContext = context.applicationContext

    fun reconnectIfNeeded(): Boolean = BridgeColdStartRecoveryPolicy.attemptRecovery(
        paired = credentialStore.load() != null,
        settingsStore = settingsStore,
    ) {
        val intent = Intent(appContext, BridgeConnectionService::class.java)
            .setAction(BridgeConnectionService.ACTION_RECONNECT)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrDefault(false)
    }
}
