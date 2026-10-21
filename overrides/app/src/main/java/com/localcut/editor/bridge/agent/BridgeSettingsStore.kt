package com.localcut.editor.bridge.agent

import android.content.Context

interface BridgeSettingsStore {
    var sessionEnabled: Boolean
    var alwaysConnected: Boolean

    fun shouldRecoverAfterBoot(): Boolean = sessionEnabled && alwaysConnected

    fun markManualDisconnect() {
        sessionEnabled = false
    }
}

class InMemoryBridgeSettingsStore(
    override var sessionEnabled: Boolean = false,
    override var alwaysConnected: Boolean = false,
) : BridgeSettingsStore

class SharedPreferencesBridgeSettingsStore(context: Context) : BridgeSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override var sessionEnabled: Boolean
        get() = preferences.getBoolean(KEY_SESSION_ENABLED, false)
        set(value) {
            check(preferences.edit().putBoolean(KEY_SESSION_ENABLED, value).commit()) {
                "failed to persist bridge sessionEnabled"
            }
        }

    override var alwaysConnected: Boolean
        get() = preferences.getBoolean(KEY_ALWAYS_CONNECTED, false)
        set(value) {
            check(preferences.edit().putBoolean(KEY_ALWAYS_CONNECTED, value).commit()) {
                "failed to persist bridge alwaysConnected"
            }
        }

    companion object {
        const val PREFERENCES_NAME = "localcut.bridge.settings.v1"
        private const val KEY_SESSION_ENABLED = "session_enabled"
        private const val KEY_ALWAYS_CONNECTED = "always_connected"
    }
}
