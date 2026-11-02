package com.localcut.editor.bridge.agent

import android.content.Context

interface BridgeSettingsStore {
    var sessionEnabled: Boolean
    var alwaysConnected: Boolean
    var relayBaseUrl: String

    fun shouldRecoverAfterBoot(): Boolean = sessionEnabled && alwaysConnected

    fun markManualDisconnect() {
        sessionEnabled = false
    }
}

class InMemoryBridgeSettingsStore(
    override var sessionEnabled: Boolean = false,
    override var alwaysConnected: Boolean = false,
    override var relayBaseUrl: String = "",
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

    override var relayBaseUrl: String
        get() = preferences
            .getString(KEY_RELAY_BASE_URL, OFFICIAL_RELAY_BASE_URL)
            ?.trim()
            .orEmpty()
            .ifBlank { OFFICIAL_RELAY_BASE_URL }
        set(value) {
            check(preferences.edit().putString(KEY_RELAY_BASE_URL, value).commit()) {
                "failed to persist bridge relayBaseUrl"
            }
        }

    companion object {
        const val PREFERENCES_NAME = "localcut.bridge.settings.v1"
        const val OFFICIAL_RELAY_BASE_URL = "https://localcut-bridge-v1.onrender.com"
        private const val KEY_SESSION_ENABLED = "session_enabled"
        private const val KEY_ALWAYS_CONNECTED = "always_connected"
        private const val KEY_RELAY_BASE_URL = "relay_base_url"
    }
}
