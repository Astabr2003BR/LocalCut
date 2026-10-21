package com.localcut.editor.bridge.security

import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.bridge.protocol.BridgeResultEnvelope

sealed interface ReplayDecision {
    data object Reserved : ReplayDecision
    data object DuplicatePending : ReplayDecision
    data class Completed(val result: BridgeResultEnvelope) : ReplayDecision
    data class Rejected(val code: BridgeErrorCode) : ReplayDecision
}

class ReplayGuard(private val maxEntries: Int = 512) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private sealed interface EntryState {
        data object Pending : EntryState
        data class Completed(val result: BridgeResultEnvelope) : EntryState
    }

    private data class Entry(
        val messageId: String,
        val nonce: String,
        val expiresAtEpochMs: Long,
        var state: EntryState,
    )

    private val entries = LinkedHashMap<String, Entry>()
    private val nonceOwners = mutableMapOf<String, String>()

    @Synchronized
    fun validateAndReserve(
        messageId: String,
        nonce: String,
        expiresAtEpochMs: Long,
        nowEpochMs: Long,
    ): ReplayDecision {
        if (expiresAtEpochMs <= nowEpochMs) {
            return ReplayDecision.Rejected(BridgeErrorCode.COMMAND_EXPIRED)
        }
        if (messageId.isBlank() || nonce.isBlank()) {
            return ReplayDecision.Rejected(BridgeErrorCode.INVALID_ARGUMENT)
        }

        val existing = entries[messageId]
        if (existing != null) {
            if (existing.nonce != nonce) {
                return ReplayDecision.Rejected(BridgeErrorCode.REPLAY_REJECTED)
            }
            return when (val state = existing.state) {
                EntryState.Pending -> ReplayDecision.DuplicatePending
                is EntryState.Completed -> ReplayDecision.Completed(state.result)
            }
        }

        val nonceOwner = nonceOwners[nonce]
        if (nonceOwner != null && nonceOwner != messageId) {
            return ReplayDecision.Rejected(BridgeErrorCode.REPLAY_REJECTED)
        }

        evictCompletedUntilRoom()
        if (entries.size >= maxEntries) {
            return ReplayDecision.Rejected(BridgeErrorCode.RATE_LIMITED)
        }

        val entry = Entry(messageId, nonce, expiresAtEpochMs, EntryState.Pending)
        entries[messageId] = entry
        nonceOwners[nonce] = messageId
        return ReplayDecision.Reserved
    }

    @Synchronized
    fun complete(messageId: String, result: BridgeResultEnvelope) {
        val entry = entries[messageId] ?: return
        entry.state = EntryState.Completed(result)
    }

    @Synchronized
    fun release(messageId: String) {
        val removed = entries.remove(messageId) ?: return
        nonceOwners.remove(removed.nonce, messageId)
    }

    private fun evictCompletedUntilRoom() {
        while (entries.size >= maxEntries) {
            val candidate = entries.values.firstOrNull { it.state is EntryState.Completed } ?: return
            entries.remove(candidate.messageId)
            nonceOwners.remove(candidate.nonce, candidate.messageId)
        }
    }
}
