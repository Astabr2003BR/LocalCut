package com.localcut.editor.bridge.agent

import com.localcut.editor.ui.editor.EditorAiReviewSource
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BridgeReviewHistoryTest {
    private class Memory : ReviewHistoryStorage {
        var raw: String? = null
        var fail = false
        override fun read(): String? = raw
        override fun write(value: String): Boolean {
            if (fail) return false
            raw = value
            return true
        }
    }
    private fun proposal(project: String = "p", rev: Long = 5L) = BridgeReviewProposal(
        UUID.randomUUID().toString(), EditorAiReviewSource(project, rev, UUID.randomUUID().toString(), "a".repeat(64)),
        "{\"privatePlan\":\"content://secret/video\"}")

    @Test fun metadataPersistsWithoutPlanAndNeverRestoresAuthorization() {
        val storage = Memory()
        val history = BridgeReviewHistory(storage)
        val first = proposal()
        history.received(first)
        history.received(first)
        history.transition(first.id, ReviewHistoryStatus.LOADED)
        assertEquals(1, history.view.value.entries.size)
        assertFalse(storage.raw!!.contains("privatePlan"))
        assertFalse(storage.raw!!.contains("content://"))
        val reopened = BridgeReviewHistory(storage)
        assertEquals(ReviewHistoryStatus.INTERRUPTED, reopened.view.value.entries.single().status)
        reopened.transition(first.id, ReviewHistoryStatus.APPLIED_IN_EDITOR, 6L)
        assertEquals(ReviewHistoryStatus.INTERRUPTED, reopened.view.value.entries.single().status)
        val second = proposal("other")
        reopened.received(second)
        reopened.transition(second.id, ReviewHistoryStatus.LOADED)
        reopened.transition(second.id, ReviewHistoryStatus.APPLIED_IN_EDITOR, 6L)
        val final = BridgeReviewHistory(storage).view.value.entries.first()
        assertEquals(6L, final.resultRevision)
        assertEquals(ReviewHistoryStatus.APPLIED_IN_EDITOR, final.status)
    }
    @Test fun terminalStatesBoundsAndFailurePreserveData() {
        val storage = Memory()
        val history = BridgeReviewHistory(storage)
        val first = proposal()
        history.received(first)
        history.transition(first.id, ReviewHistoryStatus.APPLIED_IN_EDITOR, 6L)
        assertEquals(ReviewHistoryStatus.RECEIVED, history.view.value.entries.single().status)
        history.transition(first.id, ReviewHistoryStatus.DISCARDED)
        history.transition(first.id, ReviewHistoryStatus.LOADED)
        assertEquals(ReviewHistoryStatus.DISCARDED, history.view.value.entries.single().status)
        repeat(70) { history.received(proposal()) }
        assertEquals(64, history.view.value.entries.size)
        history.supersedePending()
        assertTrue(history.view.value.entries.all { it.status == ReviewHistoryStatus.SUPERSEDED })
        val before = storage.raw
        storage.fail = true
        history.received(proposal())
        assertFalse(history.view.value.storageOk)
        assertEquals(before, storage.raw)
        val bad = Memory().apply { raw = "corrupt" }
        val isolated = BridgeReviewHistory(bad)
        assertFalse(isolated.view.value.storageOk)
        isolated.received(proposal())
        assertEquals("corrupt", bad.raw)
    }
    @Test fun lifecycleInterruptionAndExplicitDiscardHaveDistinctTerminalOutcomes() {
        val storage = Memory()
        val history = BridgeReviewHistory(storage)
        val interrupted = proposal()
        history.received(interrupted)
        // Only a loaded proposal can be interrupted by closing the UI lifecycle.
        history.transition(interrupted.id, ReviewHistoryStatus.INTERRUPTED)
        assertEquals(ReviewHistoryStatus.RECEIVED, history.view.value.entries.single().status)
        history.transition(interrupted.id, ReviewHistoryStatus.LOADED)
        history.transition(interrupted.id, ReviewHistoryStatus.INTERRUPTED)
        assertEquals(ReviewHistoryStatus.INTERRUPTED, history.view.value.entries.single().status)
        history.transition(interrupted.id, ReviewHistoryStatus.APPLIED_IN_EDITOR, 6L)
        history.transition(interrupted.id, ReviewHistoryStatus.DISCARDED)
        assertEquals(ReviewHistoryStatus.INTERRUPTED, history.view.value.entries.single().status)
        assertEquals(-1L, BridgeReviewHistory(storage).view.value.entries.single().resultRevision)

        val discarded = proposal()
        history.received(discarded)
        history.transition(discarded.id, ReviewHistoryStatus.LOADED)
        history.transition(discarded.id, ReviewHistoryStatus.DISCARDED)
        assertEquals(ReviewHistoryStatus.DISCARDED, BridgeReviewHistory(storage).view.value.entries.first().status)
        assertEquals(ReviewHistoryStatus.INTERRUPTED, BridgeReviewHistory(storage).view.value.entries.last().status)
        assertFalse(storage.raw!!.contains("privatePlan"))
        assertFalse(storage.raw!!.contains("content://secret"))
    }

    @Test fun registryJournalsReceiptLoadAndNewExportWithoutPersistingProposal() {
        val storage = Memory()
        val history = BridgeReviewHistory(storage)
        val registry = BridgeJobRegistry().apply { attachReviewHistory(history) }
        val job = (registry.reserveExport("one", "p", 5L) as BridgeJobReservation.Started).job
        val sample = BridgeExportSample(0L, 1, 1, byteArrayOf(-1, -40, -1, -39))
        val source = EditorAiReviewSource("p", 5L, job.jobId, "a".repeat(64))
        registry.succeedWithReview(job.jobId, "{}", "p", 5L, BridgeExportInspection("{}", source.sha256, listOf(sample)))
        val pending = registry.offerReviewProposal(source, "{\"private\":true}")!!
        assertEquals(ReviewHistoryStatus.RECEIVED, history.view.value.entries.single().status)
        assertEquals(pending, registry.offerReviewProposal(source, pending.planJson))
        assertEquals(1, history.view.value.entries.size)
        assertEquals(pending, registry.takeReviewProposal(pending.id, "p", 5L))
        assertEquals(ReviewHistoryStatus.LOADED, history.view.value.entries.single().status)
        registry.reserveExport("two", "p", 5L)
        assertEquals(ReviewHistoryStatus.SUPERSEDED, history.view.value.entries.single().status)
        assertFalse(storage.raw!!.contains("private"))
        assertNull(registry.reviewProposal.value)
    }
}
