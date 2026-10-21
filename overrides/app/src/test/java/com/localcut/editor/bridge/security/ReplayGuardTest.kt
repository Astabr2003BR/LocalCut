package com.localcut.editor.bridge.security

import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.bridge.protocol.BridgeResultEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGuardTest {
    @Test
    fun freshCommandIsReservedThenCompletedRetryReturnsOriginalResult() {
        val guard = ReplayGuard(maxEntries = 8)
        val first = guard.validateAndReserve("msg-1", "nonce-1", expiresAtEpochMs = 2_000L, nowEpochMs = 1_000L)
        assertTrue(first is ReplayDecision.Reserved)

        val pendingRetry = guard.validateAndReserve("msg-1", "nonce-1", expiresAtEpochMs = 2_000L, nowEpochMs = 1_100L)
        assertTrue(pendingRetry is ReplayDecision.DuplicatePending)

        val result = BridgeResultEnvelope(
            messageId = "msg-1",
            success = true,
            code = null,
            projectRevision = 6L,
            resultJson = "{\"ok\":true}",
            retryable = false,
        )
        guard.complete("msg-1", result)

        val completedRetry = guard.validateAndReserve("msg-1", "nonce-1", expiresAtEpochMs = 2_000L, nowEpochMs = 1_200L)
        assertTrue(completedRetry is ReplayDecision.Completed)
        assertEquals(result, (completedRetry as ReplayDecision.Completed).result)
    }

    @Test
    fun expiredCommandIsRejectedWithoutReservation() {
        val guard = ReplayGuard(maxEntries = 8)
        val decision = guard.validateAndReserve("msg-1", "nonce-1", expiresAtEpochMs = 999L, nowEpochMs = 1_000L)
        assertEquals(BridgeErrorCode.COMMAND_EXPIRED, (decision as ReplayDecision.Rejected).code)
    }

    @Test
    fun nonceCannotBeReusedByDifferentMessage() {
        val guard = ReplayGuard(maxEntries = 8)
        assertTrue(guard.validateAndReserve("msg-1", "nonce-1", 2_000L, 1_000L) is ReplayDecision.Reserved)

        val replay = guard.validateAndReserve("msg-2", "nonce-1", 2_000L, 1_100L)
        assertEquals(BridgeErrorCode.REPLAY_REJECTED, (replay as ReplayDecision.Rejected).code)
    }

    @Test
    fun boundedCacheEvictsOldCompletedEntries() {
        val guard = ReplayGuard(maxEntries = 2)
        fun complete(messageId: String, nonce: String, revision: Long) {
            assertTrue(guard.validateAndReserve(messageId, nonce, 10_000L, 1_000L) is ReplayDecision.Reserved)
            guard.complete(messageId, BridgeResultEnvelope(messageId, true, null, revision, "{}", false))
        }

        complete("msg-1", "nonce-1", 1L)
        complete("msg-2", "nonce-2", 2L)
        complete("msg-3", "nonce-3", 3L)

        val evicted = guard.validateAndReserve("msg-1", "nonce-1", 10_000L, 2_000L)
        assertTrue(evicted is ReplayDecision.Reserved)
    }
}
