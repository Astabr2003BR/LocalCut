package com.localcut.editor.media

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportLifecycleCoordinatorSafetyTest {
    @Test
    fun throwingDestinationCommitBecomesFailureAndCleansTempFile() {
        val tempFile = File.createTempFile("localcut-export-test-", ".tmp")
        val events = mutableListOf<ExportEvent>()
        var discarded = false
        val destination = object : ExportDestination {
            override fun commitFrom(tempFile: File): Result<Unit> {
                error("storage provider crashed")
            }

            override fun discard(): Result<Unit> {
                discarded = true
                return Result.success(Unit)
            }
        }
        val coordinator = ExportLifecycleCoordinator(tempFile, destination, events::add)

        coordinator.started()
        coordinator.renderCompleted()

        assertTrue(events.last() is ExportEvent.Failed)
        assertTrue(discarded)
        assertFalse(tempFile.exists())
        assertTrue(coordinator.isTerminal())
    }

    @Test
    fun throwingDiscardCannotPreventCancellationCleanup() {
        val tempFile = File.createTempFile("localcut-cancel-test-", ".tmp")
        val events = mutableListOf<ExportEvent>()
        val destination = object : ExportDestination {
            override fun commitFrom(tempFile: File): Result<Unit> = Result.success(Unit)
            override fun discard(): Result<Unit> = error("provider discard crashed")
        }
        val coordinator = ExportLifecycleCoordinator(tempFile, destination, events::add)

        coordinator.started()
        coordinator.cancel()

        assertTrue(events.last() is ExportEvent.Cancelled)
        assertFalse(tempFile.exists())
        assertTrue(coordinator.isTerminal())
    }
}
