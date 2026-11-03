package com.localcut.editor.bridge.agent

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.localcut.editor.bridge.security.AndroidKeystoreBridgeCredentialStore
import com.localcut.editor.bridge.security.AndroidKeystoreDeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgeConnectionCoordinator
import com.localcut.editor.bridge.transport.BridgeEndpointConfig
import com.localcut.editor.bridge.transport.OkHttpBridgePairingClient
import com.localcut.editor.diagnostics.ProcessExitDiagnostics
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BridgeConnectionService : Service() {
    private lateinit var settings: BridgeSettingsStore
    private lateinit var notificationFactory: BridgeNotificationFactory
    private lateinit var coordinatorLifecycle: BridgeCoordinatorLifecycle<BridgeConnectionCoordinator>
    private var backgroundCredentialManager: BridgeBackgroundCredentialManager? = null
    private val backgroundReconnectRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val coordinator: BridgeConnectionCoordinator
        get() = coordinatorLifecycle.value

    override fun onCreate() {
        super.onCreate()
        ProcessExitDiagnostics.mark(this, "service:create")
        settings = SharedPreferencesBridgeSettingsStore(this)
        notificationFactory = BridgeNotificationFactory(this)
        coordinatorLifecycle = BridgeCoordinatorLifecycle(
            factory = { BridgeServiceRuntime.createCoordinator(this, settings) },
            shutdownAction = { it.shutdown() },
        )
        backgroundCredentialManager = BridgeEndpointConfig.resolve(settings.relayBaseUrl).getOrNull()?.let { endpoint ->
            BridgeBackgroundCredentialManager(
                credentialStore = AndroidKeystoreBridgeCredentialStore(applicationContext),
                client = OkHttpBridgePairingClient(endpoint.httpsBaseUrl),
                identityStore = AndroidKeystoreDeviceIdentityStore(),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ProcessExitDiagnostics.mark(
            this,
            "service:start:${intent?.action?.substringAfterLast('.') ?: "sticky"}:session=${settings.sessionEnabled}",
        )
        return when (intent?.action) {
            ACTION_PREPARE_CONNECT -> {
                // Pairing and credential renewal run outside the service. Keep this path deliberately
                // lightweight: becoming foreground here protects the process before any editor/Media3
                // runtime is requested by an authenticated Bridge connection.
                if (!startAsForegroundSafely("Aguardando autorização do ChatGPT…", startId)) {
                    return START_NOT_STICKY
                }
                ProcessExitDiagnostics.mark(this, "service:prepare-connect")
                START_NOT_STICKY
            }

            ACTION_STOP_PREPARE_CONNECT -> {
                if (!settings.sessionEnabled && !coordinatorLifecycle.isInitialized) {
                    ProcessExitDiagnostics.mark(this, "service:stop-prepare-connect")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
                START_NOT_STICKY
            }

            ACTION_DISCONNECT -> {
                if (coordinatorLifecycle.isInitialized) {
                    coordinator.disconnectFromUser()
                } else {
                    settings.markManualDisconnect()
                }
                ProcessExitDiagnostics.mark(this, "service:disconnect")
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
                reconnectWithFreshCredential(startId, "Reconectando ao ChatGPT…")
                if (settings.sessionEnabled) START_STICKY else START_NOT_STICKY
            }

            else -> {
                if (settings.sessionEnabled) {
                    if (!startAsForegroundSafely("Restaurando conexão…", startId)) return START_NOT_STICKY
                    reconnectWithFreshCredential(startId, "Restaurando conexão…")
                    START_STICKY
                } else {
                    ProcessExitDiagnostics.mark(this, "service:sticky-disabled")
                    stopSelf(startId)
                    START_NOT_STICKY
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val decision = BridgeTaskRemovalPolicy.decide(
            sessionEnabled = settings.sessionEnabled,
            alwaysConnected = settings.alwaysConnected,
        )
        ProcessExitDiagnostics.mark(
            this,
            "service:task-removed:preserve=${decision.preserveConnection}:reconnect=${decision.reconnectImmediately}",
        )

        if (decision.preserveConnection) {
            // Removing the editor task is not the same as killing this foreground service.
            // Keep the currently owned WebSocket alive. Starting another connection from
            // onTaskRemoved() races with the active socket and can report
            // "bridge socket already active". If Android/OEM later kills this service/process,
            // START_STICKY and BridgeColdStartRecovery handle the real reconnect path.
            updateForegroundSafely("Agente ativo em segundo plano")
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ProcessExitDiagnostics.mark(
            this,
            "service:destroy:init=${::coordinatorLifecycle.isInitialized && coordinatorLifecycle.isInitialized}",
        )
        serviceScope.cancel()
        if (::coordinatorLifecycle.isInitialized) {
            coordinatorLifecycle.shutdownIfInitialized()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun reconnectWithFreshCredential(startId: Int, successStatus: String) {
        if (!backgroundReconnectRunning.compareAndSet(false, true)) {
            ProcessExitDiagnostics.mark(this, "service:reconnect:already-running")
            return
        }

        serviceScope.launch {
            try {
                val credentialManager = backgroundCredentialManager
                if (credentialManager == null) {
                    ProcessExitDiagnostics.mark(this@BridgeConnectionService, "service:reconnect:endpoint-invalid")
                    updateForegroundSafely("Abra o LocalCut para revisar o Relay")
                    return@launch
                }

                val freshResult = withContext(Dispatchers.IO) {
                    credentialManager.ensureFresh()
                }
                val fresh = freshResult.getOrElse { error ->
                    ProcessExitDiagnostics.mark(
                        this@BridgeConnectionService,
                        "service:credential-refresh:failed=${error.message}",
                    )
                    updateForegroundSafely("Reconexão temporariamente indisponível")
                    return@launch
                }

                if (!fresh) {
                    ProcessExitDiagnostics.mark(this@BridgeConnectionService, "service:credential-refresh:pairing-required")
                    settings.sessionEnabled = false
                    updateForegroundSafely("Pareamento necessário")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return@launch
                }

                ProcessExitDiagnostics.mark(this@BridgeConnectionService, "service:credential-refresh:ready")
                val result = coordinator.reconnectIfEnabled()
                updateForegroundSafely(
                    if (result.isSuccess) successStatus else "Toque no LocalCut para concluir a conexão",
                )
            } finally {
                backgroundReconnectRunning.set(false)
            }
        }
    }

    private fun startAsForegroundSafely(status: String, startId: Int): Boolean =
        BridgeForegroundServiceGuard.tryStart(
            start = {
                startAsForeground(status)
                ProcessExitDiagnostics.mark(this, "service:foreground")
            },
            onFailure = {
                ProcessExitDiagnostics.mark(this, "service:foreground-failed")
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
            ProcessExitDiagnostics.mark(this, "service:notify-failed")
            runCatching { notificationFactory.postReconnectFallback() }
        }
    }

    companion object {
        const val ACTION_PREPARE_CONNECT = "com.localcut.editor.bridge.action.PREPARE_CONNECT"
        const val ACTION_STOP_PREPARE_CONNECT = "com.localcut.editor.bridge.action.STOP_PREPARE_CONNECT"
        const val ACTION_CONNECT = "com.localcut.editor.bridge.action.CONNECT"
        const val ACTION_DISCONNECT = "com.localcut.editor.bridge.action.DISCONNECT"
        const val ACTION_RECONNECT = "com.localcut.editor.bridge.action.RECONNECT"
    }
}
