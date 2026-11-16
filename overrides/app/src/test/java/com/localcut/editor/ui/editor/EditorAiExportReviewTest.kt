package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.BridgeJobRegistry
import com.localcut.editor.bridge.agent.BridgeJobReservation
import com.localcut.editor.bridge.agent.BridgeJobSnapshot
import com.localcut.editor.bridge.agent.BridgeJobState
import org.junit.Assert.*
import org.junit.Test

class EditorAiExportReviewTest {
    private val hash = "a".repeat(64)
    private fun report(status: String = "technical_checks_passed", dark: String = "[]", encoded: Int = 3, durationMatch: Boolean = true) =
        """{"sourceProjectId":"p","sourceRevision":5,"technicalReview":{"status":"$status","width":1080,"height":1920,"durationUs":2000000,"audioTracks":1,"decodedFrames":3,"sampledFrames":3,"encodedSampleFrames":$encoded,"nearBlackSamplePositionsUs":$dark,"durationMatches":$durationMatch,"resolutionMatches":true,"sha256":"$hash","visualQualityVerified":false}}"""
    private fun job(json: String? = report(), state: BridgeJobState = BridgeJobState.SUCCEEDED) =
        BridgeJobSnapshot("job", "msg", state, 100, resultJson = json, sourceProjectId = "p", sourceRevision = 5L)

    @Test fun completedReportUsesExactRevisionAndNoVisualApproval() {
        val review = EditorAiExportReview.from(job(), "p", 5L)
        assertTrue(review.title.contains("verificações técnicas"))
        assertTrue(review.detail.contains("1080×1920"))
        assertTrue(review.detail.contains("não aprovação visual"))
        assertTrue(review.request!!.contains("get_export_review_frames"))
        assertTrue(review.request!!.contains(hash))
        assertTrue(review.request!!.contains("\"sourceRevision\":5"))
        assertEquals(EditorAiReviewSource("p", 5L, "job", hash), review.source)
        assertTrue(review.request!!.contains("findings"))
    }
    @Test fun staleAndForeignExportNeverOfferCurrentReview() {
        val old = EditorAiExportReview.from(job(), "p", 6L)
        assertTrue(old.detail.contains("não representa a versão atual"))
        assertNull(old.request)
        assertNull(old.source)
        assertNull(EditorAiExportReview.from(job(), "other", 5L).request)
        assertTrue(EditorAiExportReview.from(job(), "other", 5L).title.contains("Sem exportação"))
    }
    @Test fun warnsForDarkAndMismatchAndRejectsInvalidOrUnavailableEvidence() {
        val warn = EditorAiExportReview.from(job(report("needs_review", "[0]", durationMatch = false)), "p", 5L)
        assertTrue(warn.title.contains("requer revisão"))
        assertTrue(warn.detail.contains("quase preta"))
        assertTrue(warn.detail.contains("Duração diferente"))
        for (json in listOf(null, "{}", report().replace("\"sourceRevision\":5", "\"sourceRevision\":6"), report().replace(hash, "bad"))) {
            val invalid = EditorAiExportReview.from(job(json), "p", 5L)
            assertTrue(invalid.title.contains("indisponível"))
            assertNull(invalid.request)
        }
        assertNull(EditorAiExportReview.from(job(report(encoded = 2)), "p", 5L).request)
        assertNull(EditorAiExportReview.from(job(report(encoded = 2)), "p", 5L).source)
    }
    @Test fun latestRegistryTracksProgressAndIgnoresOldCallbacks() {
        val registry = BridgeJobRegistry()
        val first = (registry.reserveExport("one", "p", 5L) as BridgeJobReservation.Started).job
        assertEquals(first, registry.latest.value)
        registry.updateProgress(first.jobId, 43)
        assertTrue(EditorAiExportReview.from(registry.latest.value, "p", 5L).title.contains("43%"))
        registry.succeed(first.jobId, report())
        assertTrue(EditorAiExportReview.from(registry.latest.value, "p", 5L).title.contains("verificações técnicas"))
        val second = (registry.reserveExport("two", "p", 6L) as BridgeJobReservation.Started).job
        registry.markRunning(first.jobId)
        registry.fail(first.jobId, com.localcut.editor.bridge.protocol.BridgeErrorCode.EXPORT_FAILED, "late")
        assertEquals(BridgeJobState.SUCCEEDED, registry.get(first.jobId)!!.state)
        assertEquals(second, registry.latest.value)
    }
}
