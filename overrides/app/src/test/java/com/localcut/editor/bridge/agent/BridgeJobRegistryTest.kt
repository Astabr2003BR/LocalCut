package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeJobRegistryTest {
    @Test
    fun sameMessageIdReturnsSameExportJobAndDifferentMessageIsBusyWhileActive() {
        val registry = BridgeJobRegistry()
        val first = registry.reserveExport("msg-1") as BridgeJobReservation.Started
        val retry = registry.reserveExport("msg-1") as BridgeJobReservation.Existing
        val busy = registry.reserveExport("msg-2") as BridgeJobReservation.Busy

        assertEquals(first.job.jobId, retry.job.jobId)
        assertEquals(first.job.jobId, busy.activeJob.jobId)
        assertEquals(BridgeJobState.QUEUED, first.job.state)
    }

    @Test
    fun exportProgressAndCompletionAreQueryableAndReleaseActiveSlot() {
        val registry = BridgeJobRegistry()
        val first = registry.reserveExport("msg-1") as BridgeJobReservation.Started

        registry.markRunning(first.job.jobId)
        registry.updateProgress(first.job.jobId, 47)
        assertEquals(BridgeJobState.RUNNING, registry.get(first.job.jobId)!!.state)
        assertEquals(47, registry.get(first.job.jobId)!!.progressPercent)

        registry.succeed(first.job.jobId, resultJson = "{\"destination\":\"LOCALCUT_MEDIASTORE\"}")
        val completed = registry.get(first.job.jobId)!!
        assertEquals(BridgeJobState.SUCCEEDED, completed.state)
        assertEquals(100, completed.progressPercent)
        assertTrue(registry.reserveExport("msg-2") is BridgeJobReservation.Started)

        val completedRetry = registry.reserveExport("msg-1") as BridgeJobReservation.Existing
        assertEquals(first.job.jobId, completedRetry.job.jobId)
        assertEquals(BridgeJobState.SUCCEEDED, completedRetry.job.state)
    }

    @Test
    fun failureAndCancellationBecomeTerminalWithStableMetadata() {
        val registry = BridgeJobRegistry()
        val failed = (registry.reserveExport("msg-fail") as BridgeJobReservation.Started).job
        registry.markRunning(failed.jobId)
        registry.fail(failed.jobId, BridgeErrorCode.EXPORT_FAILED, "encoder failed")
        assertEquals(BridgeJobState.FAILED, registry.get(failed.jobId)!!.state)
        assertEquals(BridgeErrorCode.EXPORT_FAILED, registry.get(failed.jobId)!!.errorCode)

        val cancelled = (registry.reserveExport("msg-cancel") as BridgeJobReservation.Started).job
        registry.cancel(cancelled.jobId)
        assertEquals(BridgeJobState.CANCELLED, registry.get(cancelled.jobId)!!.state)
    }
}
