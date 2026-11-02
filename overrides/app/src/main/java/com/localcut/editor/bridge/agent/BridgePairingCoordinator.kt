package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgePairingClient
import com.localcut.editor.bridge.transport.BridgePairingStatus
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class BridgePairingCoordinator(
    private val client: BridgePairingClient,
    private val identityStore: DeviceIdentityStore,
    private val credentialStore: BridgeCredentialStore,
    private val settingsStore: BridgeSettingsStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val pollDelayMs: Long = 1_500L,
    private val pollDelay: suspend (Long) -> Unit = { delay(it) },
) {
    init {
        require(pollDelayMs >= 0L) { "pollDelayMs must be non-negative" }
    }

    suspend fun pair(onEvent: (BridgeUiEvent) -> Unit): Result<Unit> = runCatching {
        val identity = identityStore.getOrCreatePublicIdentity()
        val started = withContext(Dispatchers.IO) { client.start(identity).getOrThrow() }
        onEvent(BridgeUiEvent.Pairing(started.code))

        while (nowEpochMs() <= started.expiresAtEpochMs) {
            val status = statusWithTransientRetry(
                code = started.code,
                signatureBase64 = null,
                expiresAtEpochMs = started.expiresAtEpochMs,
            )
            when (status.state) {
                "PENDING" -> pollDelay(pollDelayMs)
                "CLAIMED" -> {
                    val challenge = status.challengeBase64
                        ?: throw IllegalStateException("PAIRING_CHALLENGE_MISSING")
                    val challengeBytes = runCatching { Base64.getDecoder().decode(challenge) }
                        .getOrElse { throw IllegalStateException("PAIRING_CHALLENGE_INVALID", it) }
                    val signatureBase64 = Base64.getEncoder().encodeToString(
                        identityStore.signChallenge(challengeBytes),
                    )
                    val completed = statusWithTransientRetry(
                        code = started.code,
                        signatureBase64 = signatureBase64,
                        expiresAtEpochMs = started.expiresAtEpochMs,
                    )
                    if (completed.state != "PAIRED") {
                        throw IllegalStateException("PAIRING_NOT_COMPLETED")
                    }
                    val credential = completed.credential
                        ?: throw IllegalStateException("PAIRING_CREDENTIAL_MISSING")
                    credentialStore.save(credential)
                    settingsStore.sessionEnabled = true
                    onEvent(BridgeUiEvent.Connecting)
                    return@runCatching Unit
                }
                "PAIRED" -> {
                    val credential = status.credential
                        ?: throw IllegalStateException("PAIRING_CREDENTIAL_MISSING")
                    credentialStore.save(credential)
                    settingsStore.sessionEnabled = true
                    onEvent(BridgeUiEvent.Connecting)
                    return@runCatching Unit
                }
                "EXPIRED" -> throw IllegalStateException("PAIRING_EXPIRED")
                else -> throw IllegalStateException("PAIRING_STATE_INVALID")
            }
        }
        throw IllegalStateException("PAIRING_EXPIRED")
    }.onFailure { error ->
        onEvent(BridgeUiEvent.Error(error.message ?: "Falha no pareamento"))
    }

    private suspend fun statusWithTransientRetry(
        code: String,
        signatureBase64: String?,
        expiresAtEpochMs: Long,
    ): BridgePairingStatus {
        while (nowEpochMs() <= expiresAtEpochMs) {
            val result = withContext(Dispatchers.IO) {
                client.status(code, signatureBase64)
            }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull() ?: error("PAIRING_STATUS_FAILED")
            if (error !is IOException) throw error
            pollDelay(pollDelayMs)
        }
        throw IllegalStateException("PAIRING_EXPIRED")
    }
}
