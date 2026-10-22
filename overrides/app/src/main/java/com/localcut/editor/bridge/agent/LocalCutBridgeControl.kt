package com.localcut.editor.bridge.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import com.localcut.editor.bridge.security.AndroidKeystoreBridgeCredentialStore
import com.localcut.editor.bridge.security.AndroidKeystoreDeviceIdentityStore
import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeMobilePairingProofFactory
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgeConnectionObserver
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
    private val identityStore: DeviceIdentityStore = AndroidKeystoreDeviceIdentityStore(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val pairingRunning = AtomicBoolean(false)
    private val forcedRefreshPending = AtomicBoolean(false)
    private val endpointPreference = BridgeEndpointPreference(settingsStore, credentialStore)
    private val mutableState = MutableStateFlow(
        BridgeUiState(
            paired = credentialStore.load() != null,
            alwaysConnected = settingsStore.alwaysConnected,
            relayBaseUrl = settingsStore.relayBaseUrl,
        ),
    )
    val state: StateFlow<BridgeUiState> = mutableState.asStateFlow()

    val connectionObserver: BridgeConnectionObserver = object : BridgeConnectionObserver {
        override fun onConnected() {
            update(BridgeUiEvent.Connected)
        }

        override fun onDisconnected(code: Int?, reason: String?) {
            if (code == HTTP_UNAUTHORIZED) {
                update(BridgeUiEvent.Connecting)
                connect(forceRefreshCredential = true)
            } else if (settingsStore.sessionEnabled) {
                update(BridgeUiEvent.Connecting)
            } else {
                update(BridgeUiEvent.Disconnected)
            }
        }

        override fun onFailure(message: String) {
            update(BridgeUiEvent.Error(message))
        }
    }

    fun setRelayBaseUrl(rawBaseUrl: String) {
        endpointPreference.save(rawBaseUrl)
            .onSuccess { endpoint ->
                update(BridgeUiEvent.RelayBaseUrlChanged(endpoint.httpsBaseUrl))
            }
            .onFailure { error ->
                val message = if (error.message == "PAIRING_MUST_BE_REVOKED_BEFORE_ENDPOINT_CHANGE") {
                    "Revogue o pareamento atual antes de trocar o servidor Relay."
                } else {
                    "Use um endereço HTTPS válido do Relay, sem caminho, usuário, consulta ou fragmento."
                }
                update(BridgeUiEvent.Error(message))
            }
    }

    fun connect() {
        connect(forceRefreshCredential = false)
    }

    private fun connect(forceRefreshCredential: Boolean) {
        val endpoint = endpointPreference.current().getOrElse {
            update(BridgeUiEvent.Error("Configure um endereço HTTPS válido do Relay antes de conectar."))
            return
        }
        if (forceRefreshCredential) forcedRefreshPending.set(true)
        if (!pairingRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                val forceRefresh = forcedRefreshPending.getAndSet(false)
                val client = OkHttpBridgePairingClient(endpoint.httpsBaseUrl)
                var credential = credentialStore.load()
                if (credential != null && (forceRefresh || credential.expiresAtEpochMs <= nowEpochMs())) {
                    credential = credential.refreshToken?.let { refresh ->
                        withContext(Dispatchers.IO) { client.refresh(refresh).getOrNull() }
                    }
                    if (credential != null) {
                        credentialStore.save(credential)
                    } else {
                        credentialStore.clear()
                        mutableState.value = mutableState.value.copy(paired = false)
                    }
                }

                if (credential == null) {
                    val pairing = BridgePairingCoordinator(
                        client = client,
                        identityStore = identityStore,
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
                if (forcedRefreshPending.get()) {
                    connect(forceRefreshCredential = true)
                }
            }
        }
    }

    fun claimPendingPairingFromMobile(code: String, onComplete: (Result<Unit>) -> Unit) {
        if (!matchesPendingPairing(code)) {
            onComplete(Result.failure(IllegalStateException("PAIRING_CODE_MISMATCH")))
            return
        }
        val endpoint = endpointPreference.current().getOrElse { error ->
            onComplete(Result.failure(error))
            return
        }
        scope.launch {
            val result = runCatching {
                check(matchesPendingPairing(code)) { "PAIRING_CODE_MISMATCH" }
                val proof = BridgeMobilePairingProofFactory(
                    identityStore = identityStore,
                    nowEpochMs = nowEpochMs,
                ).create(code)
                withContext(Dispatchers.IO) {
                    OkHttpBridgePairingClient(endpoint.httpsBaseUrl)
                        .mobileClaim(proof)
                        .getOrThrow()
                }
                Unit
            }
            onComplete(result)
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
        val endpoint = endpointPreference.current().getOrElse {
            update(BridgeUiEvent.Error("O Relay configurado não é válido; restaure o servidor usado no pareamento antes de revogar."))
            return
        }
        if (!pairingRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                val client = OkHttpBridgePairingClient(endpoint.httpsBaseUrl)
                val coordinator = BridgeUnpairCoordinator(
                    client = client,
                    credentialStore = credentialStore,
                    settingsStore = settingsStore,
                    nowEpochMs = nowEpochMs,
                )
                val result = withContext(Dispatchers.IO) { coordinator.unpair() }
                result.onSuccess {
                    startBridgeService(BridgeConnectionService.ACTION_DISCONNECT, foreground = false)
                    update(BridgeUiEvent.PairingRevoked)
                }.onFailure { error ->
                    update(BridgeUiEvent.Error(error.message ?: "Não foi possível revogar o pareamento"))
                }
            } finally {
                pairingRunning.set(false)
            }
        }
    }

    private fun matchesPendingPairing(code: String): Boolean {
        val current = mutableState.value
        return current.phase == BridgeUiPhase.PAIRING && current.pairingCode == code
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

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
