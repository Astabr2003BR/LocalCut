package com.localcut.editor.bridge.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BridgeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val settings = SharedPreferencesBridgeSettingsStore(context)
        if (!settings.shouldRecoverAfterBoot()) return

        val reconnectIntent = Intent(context, BridgeConnectionService::class.java)
            .setAction(BridgeConnectionService.ACTION_RECONNECT)

        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(reconnectIntent)
            } else {
                context.startService(reconnectIntent)
            }
        }.isSuccess

        if (!started) {
            runCatching { BridgeNotificationFactory(context).postReconnectFallback() }
        }
    }
}
