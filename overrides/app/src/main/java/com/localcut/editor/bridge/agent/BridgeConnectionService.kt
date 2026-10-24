package com.localcut.editor.bridge.agent

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class BridgeConnectionService : Service() {
    private lateinit var settings: BridgeSettingsStore
    private lateinit var notificationFactory: BridgeNotificationFactory
    private val coordinator by lazy { BridgeServiceRuntime.createCoordinator(this, settings) }

    override fun onCreate() {
        super.onCreate()
        settings = SharedPreferencesBridgeSettingsStore(this)
        notificationFactory = BridgeNotificationFactory(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_DISCONNECT -> {
                coordinator.disconnectFromUser()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                START_NOT_STICKY
            }

            ACTION_CONNECT -> {
                startAsForeground("Conectando…")
                val result = coordinator.connectFromUser()
                updateForeground(
                    if (result.isSuccess) "Conectando ao ChatGPT…" else "Pareamento ou conexão necessária",
                )
                START_STICKY
            }

            ACTION_RECONNECT -> {
                startAsForeground("Reconectando…")
                val result = coordinator.reconnectIfEnabled()
                updateForeground(
                    if (result.isSuccess) "Reconectando ao ChatGPT…" else "Toque no LocalCut para concluir a conexão",
                )
                if (settings.sessionEnabled) START_STICKY else START_NOT_STICKY
            }

            else -> {
                if (settings.sessionEnabled) {
                    startAsForeground("Restaurando conexão…")
                    coordinator.reconnectIfEnabled()
                    START_STICKY
                } else {
                    stopSelf(startId)
                    START_NOT_STICKY
                }
            }
        }
    }

    override fun onDestroy() {
        coordinator.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(status: String) {
        startForeground(
            BridgeNotificationFactory.FOREGROUND_NOTIFICATION_ID,
            notificationFactory.foreground(status),
        )
        notificationFactory.cancelReconnectFallback()
    }

    private fun updateForeground(status: String) {
        getSystemService(NotificationManager::class.java).notify(
            BridgeNotificationFactory.FOREGROUND_NOTIFICATION_ID,
            notificationFactory.foreground(status),
        )
    }

    companion object {
        const val ACTION_CONNECT = "com.localcut.editor.bridge.action.CONNECT"
        const val ACTION_DISCONNECT = "com.localcut.editor.bridge.action.DISCONNECT"
        const val ACTION_RECONNECT = "com.localcut.editor.bridge.action.RECONNECT"
    }
}
