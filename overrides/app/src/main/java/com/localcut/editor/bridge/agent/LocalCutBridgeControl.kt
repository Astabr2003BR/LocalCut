package com.localcut.editor.bridge.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import com.localcut.editor.bridge.security.AndroidKeystoreBridgeCredentialStore
import com.localcut.editor.bridge.security.AndroidKeystoreDeviceIdentityStore
import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.transport.BridgeConnectionObserver
import com.localcut.editor.bridge.transport.BridgeEndpointConfig
import com.localcut.editor.bridge.transport.OkHttpBridgePairingClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalCutBridgeControl(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsStore: BridgeSettingsStore = SharedPreferencesBridgeSettingsStore(context),
    private val credentialStore: BridgeCredentialStore = AndroidKeystoreBridgeCredentialStore(context),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val pairingRunning = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(
        BridgeUiState(
            paired = credentialStore.load() != null,
            alwaysConnected = settingsStore.alwaysConnected,
        ),
    )
    val state: StateFlow<BridgeUiState> = mutableState.asStateFlow()

    val connectionObserver: BridgeConnectionObserver = object : BridgeConnectionObserver {
        override fun onConnected() {
            update(BridgeUiEvent.Connected)
        }

        override fun onDisconnected(code: Int?, reason: String?) {
            if (settingsStore.sessionEnabled) {
                update(BridgeUiEvent.Connecting)
            } else {
                update(BridgeUiEvent.Disconnected)
            }
        }

        override fun onFailure(message: String) {
            update(BridgeUiEvent.Error(message))
        }
    }

    fun connect() {
        if (!BridgeEndpointConfig.isConfigured()) {
            update(BridgeUiEvent.Error("Bridge ainda não possui endpoint de produção configurado"))
            return
        }
        if (!pairingRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                val client = OkHttpBridgePairingClient(BridgeEndpointConfig.HTTPS_BASE_URL)
                var credential = credentialStore.load()
                if (credential != null && credential.expiresAtEpochMs <= nowEpochMs()) {
                    credential = credential.refreshToken?.let { refresh ->
                        withContext(Dispatchers.IO) { client.refresh(refresh).getOrNull() }
                    }
                    if (credential != null) {
                        credentialStore.save(credential)
                    } else {
                        credentialStore.clear()
                    }
                }

                if (credential == null) {
                    val pairing = BridgePairingCoordinator(
                        client = client,
                        identityStore = AndroidKeystoreDeviceIdentityStore(),
                        credentialStore = credentialStore,
                        settingsStore = settingsStore,
                        nowEpochMs = nowEpochMs,
                    )
                    val result = pairing.pair(::update)
                    if (result.isFailure) return@launch
                    mutableState.value = mutableState.value.copy(paired = true)
                } else {
                    settingsStore.sessionEnabled = true
                    mutableState.value = mutableState.value.copy(paired = true)
                    update(BridgeUiEvent.Connecting)
                }
                startBridgeService(BridgeConnectionService.ACTION_CONNECT)
            } finally {
                pairingRunning.set(false)
            }
        }
    }

    fun disconnect() {
        settingsStore.markManualDisconnect()
        startBridgeService(BridgeConnectionService.ACTION_DISCONNECT, foreground = false)
        update(BridgeUiEvent.Disconnected)
    }

    fun setAlwaysConnected(enabled: Boolean) {
        settingsStore.alwaysConnected = enabled
        update(BridgeUiEvent.AlwaysConnectedChanged(enabled))
    }

    fun revokePairing() {
        credentialStore.clear()
        settingsStore.sessionEnabled = false
        settingsStore.alwaysConnected = false
        startBridgeService(BridgeConnectionService.ACTION_DISCONNECT, foreground = false)
        update(BridgeUiEvent.PairingRevoked)
    }

    private fun update(event: BridgeUiEvent) {
        mutableState.value = mutableState.value.reduce(event)
    }

    private fun startBridgeService(action: String, foreground: Boolean = true) {
        val intent = Intent(appContext, BridgeConnectionService::class.java).setAction(action)
        runCatching {
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { error ->
            update(BridgeUiEvent.Error(error.message ?: "Não foi possível iniciar o Bridge"))
        }
    }
}
