package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.security.BridgeCredentialStore
import com.localcut.editor.bridge.security.BridgeMobilePairingProofFactory
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgePairingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class BridgeOAuthCredentialBootstrap(
    private val client: BridgePairingClient,
    private val identityStore: DeviceIdentityStore,
    private val credentialStore: BridgeCredentialStore,
    private val pairingCodeProvider: () -> String?,
    private val startPairing: () -> Unit,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val pollDelayMs: Long = DEFAULT_POLL_DELAY_MS,
    private val pollDelay: suspend (Long) -> Unit = { delay(it) },
) {
    init {
        require(timeoutMs > 0L) { "timeoutMs must be > 0" }
        require(pollDelayMs > 0L) { "pollDelayMs must be > 0" }
    }

    suspend fun ensureCredential(): BridgeStoredCredential {
        credentialStore.load()?.let { return it }

        val deadline = nowEpochMs() + timeoutMs
        var pairingCode = pairingCodeProvider()
        if (pairingCode == null) {
            startPairing()
            pairingCode = awaitPairingCode(deadline)
        }

        val proof = BridgeMobilePairingProofFactory(identityStore, nowEpochMs).create(pairingCode)
        val claim = withContext(Dispatchers.IO) { client.mobileClaim(proof) }
        if (claim.isFailure && claim.exceptionOrNull()?.message != PAIRING_ALREADY_CLAIMED) {
            throw claim.exceptionOrNull() ?: IllegalStateException("PAIRING_MOBILE_CLAIM_FAILED")
        }

        return awaitCredential(deadline)
    }

    private suspend fun awaitPairingCode(deadline: Long): String {
        while (nowEpochMs() <= deadline) {
            pairingCodeProvider()?.let { return it }
            pollDelay(pollDelayMs)
        }
        throw IllegalStateException("PAIRING_BOOTSTRAP_TIMEOUT")
    }

    private suspend fun awaitCredential(deadline: Long): BridgeStoredCredential {
        while (nowEpochMs() <= deadline) {
            credentialStore.load()?.let { return it }
            pollDelay(pollDelayMs)
        }
        throw IllegalStateException("PAIRING_BOOTSTRAP_TIMEOUT")
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val DEFAULT_POLL_DELAY_MS = 100L
        const val PAIRING_ALREADY_CLAIMED = "PAIRING_ALREADY_CLAIMED"
    }
}
