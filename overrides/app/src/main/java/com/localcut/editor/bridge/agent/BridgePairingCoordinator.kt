package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgePairingClient
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
            val status = withContext(Dispatchers.IO) { client.status(started.code).getOrThrow() }
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
                    val completed = withContext(Dispatchers.IO) {
                        client.status(started.code, signatureBase64).getOrThrow()
                    }
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
}
