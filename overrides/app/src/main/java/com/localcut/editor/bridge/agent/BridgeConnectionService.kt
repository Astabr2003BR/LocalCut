package com.localcut.editor.bridge.agent

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.localcut.editor.bridge.transport.BridgeConnectionCoordinator

class BridgeConnectionService : Service() {
    private lateinit var settings: BridgeSettingsStore
    private lateinit var notificationFactory: BridgeNotificationFactory
    private lateinit var coordinatorLifecycle: BridgeCoordinatorLifecycle<BridgeConnectionCoordinator>

    private val coordinator: BridgeConnectionCoordinator
        get() = coordinatorLifecycle.value

    override fun onCreate() {
        super.onCreate()
        settings = SharedPreferencesBridgeSettingsStore(this)
        notificationFactory = BridgeNotificationFactory(this)
        coordinatorLifecycle = BridgeCoordinatorLifecycle(
            factory = { BridgeServiceRuntime.createCoordinator(this, settings) },
            shutdownAction = { it.shutdown() },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_DISCONNECT -> {
                if (coordinatorLifecycle.isInitialized) {
                    coordinator.disconnectFromUser()
                } else {
                    settings.markManualDisconnect()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                START_NOT_STICKY
            }

            ACTION_CONNECT -> {
                if (!startAsForegroundSafely("Conectando…", startId)) return START_NOT_STICKY
                val result = coordinator.connectFromUser()
                updateForegroundSafely(
                    if (result.isSuccess) "Conectando ao ChatGPT…" else "Pareamento ou conexão necessária",
                )
                START_STICKY
            }

            ACTION_RECONNECT -> {
                if (!startAsForegroundSafely("Reconectando…", startId)) return START_NOT_STICKY
                val result = coordinator.reconnectIfEnabled()
                updateForegroundSafely(
                    if (result.isSuccess) "Reconectando ao ChatGPT…" else "Toque no LocalCut para concluir a conexão",
                )
                if (settings.sessionEnabled) START_STICKY else START_NOT_STICKY
            }

            else -> {
                if (settings.sessionEnabled) {
                    if (!startAsForegroundSafely("Restaurando conexão…", startId)) return START_NOT_STICKY
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
        if (::coordinatorLifecycle.isInitialized) {
            coordinatorLifecycle.shutdownIfInitialized()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForegroundSafely(status: String, startId: Int): Boolean =
        BridgeForegroundServiceGuard.tryStart(
            start = { startAsForeground(status) },
            onFailure = {
                runCatching { notificationFactory.postReconnectFallback() }
                stopSelf(startId)
            },
        )

    private fun startAsForeground(status: String) {
        startForeground(
            BridgeNotificationFactory.FOREGROUND_NOTIFICATION_ID,
            notificationFactory.foreground(status),
        )
        notificationFactory.cancelReconnectFallback()
    }

    private fun updateForegroundSafely(status: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                BridgeNotificationFactory.FOREGROUND_NOTIFICATION_ID,
                notificationFactory.foreground(status),
            )
        }.onFailure {
            runCatching { notificationFactory.postReconnectFallback() }
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.localcut.editor.bridge.action.CONNECT"
        const val ACTION_DISCONNECT = "com.localcut.editor.bridge.action.DISCONNECT"
        const val ACTION_RECONNECT = "com.localcut.editor.bridge.action.RECONNECT"
    }
}
