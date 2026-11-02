package com.localcut.editor.bridge.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import com.localcut.editor.bridge.security.AndroidKeystoreBridgeCredentialStore
import com.localcut.editor.bridge.security.AndroidKeystoreDeviceIdentityStore
import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeMobilePairingProofFactory
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgeConnectionObserver
import com.localcut.editor.bridge.transport.OkHttpBridgePairingClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val oauthApprovalRunning = AtomicBoolean(false)
    private val remoteApprovalRunning = AtomicBoolean(false)
    private val forcedRefreshPending = AtomicBoolean(false)
    private val renewalRetryScheduled = AtomicBoolean(false)
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
            renewalRetryScheduled.set(false)
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
            .onSuccess { endpoint -> update(BridgeUiEvent.RelayBaseUrlChanged(endpoint.httpsBaseUrl)) }
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

        BridgeAgentConnectionLifecyclePolicy.beforeConnectAttempt().let { transition ->
            startBridgeService(transition.action, transition.foreground)
        }

        scope.launch {
            try {
                val forceRefresh = forcedRefreshPending.getAndSet(false)
                val client = OkHttpBridgePairingClient(endpoint.httpsBaseUrl)
                var credential = credentialStore.load()
                val currentCredential = credential
                if (currentCredential != null && (forceRefresh || currentCredential.expiresAtEpochMs <= nowEpochMs())) {
                    val renewal = withContext(Dispatchers.IO) {
                        BridgeCredentialRenewal(
                            client = client,
                            identityStore = identityStore,
                            nowEpochMs = nowEpochMs,
                        ).renew(currentCredential)
                    }
                    if (renewal.isFailure) {
                        update(BridgeUiEvent.Error("Reconexão temporariamente indisponível. O pareamento foi preservado."))
                        scheduleCredentialRenewalRetry()
                        return@launch
                    }
                    credential = renewal.getOrThrow()
                    if (credential != null) {
                        credentialStore.save(credential)
                        renewalRetryScheduled.set(false)
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
                BridgeAgentConnectionLifecyclePolicy.afterConnectAttempt(
                    sessionEnabled = settingsStore.sessionEnabled,
                    credentialPresent = credentialStore.load() != null,
                )?.let { transition ->
                    startBridgeService(transition.action, transition.foreground)
                }
                if (forcedRefreshPending.get()) connect(forceRefreshCredential = true)
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
                val proof = BridgeMobilePairingProofFactory(identityStore, nowEpochMs).create(code)
                withContext(Dispatchers.IO) {
                    OkHttpBridgePairingClient(endpoint.httpsBaseUrl).mobileClaim(proof).getOrThrow()
                }
                Unit
            }
            onComplete(result)
        }
    }

    fun approveOAuthRequest(requestId: String, onComplete: (Result<String>) -> Unit) {
        if (!requestId.matches(OAUTH_REQUEST_ID)) {
            onComplete(Result.failure(IllegalArgumentException("OAUTH_APPROVAL_REQUEST_INVALID")))
            return
        }
        val endpoint = endpointPreference.current().getOrElse { error ->
            onComplete(Result.failure(error))
            return
        }
        if (!oauthApprovalRunning.compareAndSet(false, true)) {
            onComplete(Result.failure(IllegalStateException("OAUTH_APPROVAL_IN_PROGRESS")))
            return
        }

        scope.launch {
            try {
                val result = runCatching {
                    val client = OkHttpBridgePairingClient(endpoint.httpsBaseUrl)
                    var credential = BridgeOAuthCredentialBootstrap(
                        client = client,
                        identityStore = identityStore,
                        credentialStore = credentialStore,
                        pairingCodeProvider = {
                            mutableState.value.takeIf { it.phase == BridgeUiPhase.PAIRING }?.pairingCode
                        },
                        startPairing = { connect() },
                        nowEpochMs = nowEpochMs,
                    ).ensureCredential()

                    if (credential.expiresAtEpochMs <= nowEpochMs()) {
                        credential = renewForOAuth(client, credential)
                    }

                    var approval = withContext(Dispatchers.IO) {
                        client.approveOAuth(requestId, credential.accessToken)
                    }
                    if (approval.isFailure && isOAuthSessionAuthFailure(approval.exceptionOrNull())) {
                        credential = renewForOAuth(client, credential)
                        approval = withContext(Dispatchers.IO) {
                            client.approveOAuth(requestId, credential.accessToken)
                        }
                    }
                    approval.getOrThrow()
                }
                onComplete(result)
            } finally {
                oauthApprovalRunning.set(false)
            }
        }
    }

    fun approveRemoteControlRequest(requestId: String, onComplete: (Result<Unit>) -> Unit) {
        if (!requestId.matches(OAUTH_REQUEST_ID)) {
            onComplete(Result.failure(IllegalArgumentException("REMOTE_APPROVAL_REQUEST_INVALID")))
            return
        }
        val endpoint = endpointPreference.current().getOrElse { error ->
            onComplete(Result.failure(error))
            return
        }
        if (!remoteApprovalRunning.compareAndSet(false, true)) {
            onComplete(Result.failure(IllegalStateException("REMOTE_APPROVAL_IN_PROGRESS")))
            return
        }

        scope.launch {
            try {
                val result = runCatching {
                    val client = OkHttpBridgePairingClient(endpoint.httpsBaseUrl)
                    var credential = BridgeOAuthCredentialBootstrap(
                        client = client,
                        identityStore = identityStore,
                        credentialStore = credentialStore,
                        pairingCodeProvider = {
                            mutableState.value.takeIf { it.phase == BridgeUiPhase.PAIRING }?.pairingCode
                        },
                        startPairing = { connect() },
                        nowEpochMs = nowEpochMs,
                    ).ensureCredential()

                    if (credential.expiresAtEpochMs <= nowEpochMs()) {
                        credential = renewForOAuth(client, credential)
                    }

                    var approval = withContext(Dispatchers.IO) {
                        client.approveRemoteControl(requestId, credential.accessToken)
                    }
                    if (approval.isFailure && isOAuthSessionAuthFailure(approval.exceptionOrNull())) {
                        credential = renewForOAuth(client, credential)
                        approval = withContext(Dispatchers.IO) {
                            client.approveRemoteControl(requestId, credential.accessToken)
                        }
                    }
                    approval.getOrThrow()
                }
                onComplete(result)
            } finally {
                remoteApprovalRunning.set(false)
            }
        }
    }

    fun disconnect() {
        settingsStore.markManualDisconnect()
        renewalRetryScheduled.set(false)
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
                    renewalRetryScheduled.set(false)
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

    private suspend fun renewForOAuth(
        client: OkHttpBridgePairingClient,
        current: BridgeStoredCredential,
    ): BridgeStoredCredential {
        val renewed = withContext(Dispatchers.IO) {
            BridgeCredentialRenewal(
                client = client,
                identityStore = identityStore,
                nowEpochMs = nowEpochMs,
            ).renew(current)
        }.getOrElse { throw it }
            ?: run {
                credentialStore.clear()
                mutableState.value = mutableState.value.copy(paired = false)
                throw IllegalStateException("PAIRING_REQUIRED")
            }
        credentialStore.save(renewed)
        mutableState.value = mutableState.value.copy(paired = true)
        return renewed
    }

    private fun isOAuthSessionAuthFailure(error: Throwable?): Boolean = when (error?.message) {
        "AUTH_REQUIRED", "REFRESH_TOKEN_INVALID" -> true
        else -> false
    }

    private fun scheduleCredentialRenewalRetry() {
        if (!settingsStore.sessionEnabled) return
        if (!renewalRetryScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(CREDENTIAL_RETRY_DELAY_MS)
            renewalRetryScheduled.set(false)
            if (settingsStore.sessionEnabled && credentialStore.load() != null) {
                connect(forceRefreshCredential = true)
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
        const val CREDENTIAL_RETRY_DELAY_MS = 10_000L
        val OAUTH_REQUEST_ID = Regex("^[A-Za-z0-9_-]{16,256}$")
    }
}
