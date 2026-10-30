package com.localcut.editor.diagnostics

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitDiagnosticsPolicyTest {
    @Test
    fun crashAnrAndMemoryDeathsAreSurfaced() {
        assertTrue(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_CRASH))
        assertTrue(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertTrue(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_ANR))
        assertTrue(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertTrue(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_SIGNALED))
        assertTrue(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_OTHER))
    }

    @Test
    fun expectedUserAndPackageExitsAreNotSurfacedAsFailures() {
        assertFalse(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertFalse(ProcessExitDiagnosticsPolicy.shouldSurface(ApplicationExitInfo.REASON_PACKAGE_UPDATED))
    }

    @Test
    fun labelsRemainReadableForOnDeviceDiagnosis() {
        assertEquals("CRASH_JAVA", ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_CRASH))
        assertEquals("CRASH_NATIVE", ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals("ANR", ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_ANR))
        assertEquals("LOW_MEMORY", ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertEquals("SIGNALED", ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_SIGNALED))
        assertEquals("OTHER", ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_OTHER))
    }

    @Test
    fun swipeUpCleanIsClassifiedAsRecentAppsCleanupOnlyForReasonOther() {
        assertEquals(
            "LIMPEZA DOS RECENTES",
            ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_OTHER, "SwipeUpClean"),
        )
        assertEquals(
            "LIMPEZA DOS RECENTES",
            ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_OTHER, " swipe_up-clean "),
        )
        assertEquals(
            "OTHER",
            ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_OTHER, "vendor-other"),
        )
        assertEquals(
            "LOW_MEMORY",
            ProcessExitDiagnosticsPolicy.labelFor(ApplicationExitInfo.REASON_LOW_MEMORY, "SwipeUpClean"),
        )
    }

    @Test
    fun swipeUpCleanExplanationDoesNotMisclassifyMemoryPressure() {
        assertEquals(
            "Encerramento solicitado pelo launcher/OEM ao limpar o app dos recentes; não classificado como pressão de memória.",
            ProcessExitDiagnosticsPolicy.interpretationFor(ApplicationExitInfo.REASON_OTHER, "SwipeUpClean"),
        )
        assertNull(
            ProcessExitDiagnosticsPolicy.interpretationFor(ApplicationExitInfo.REASON_OTHER, "vendor-other"),
        )
        assertNull(
            ProcessExitDiagnosticsPolicy.interpretationFor(ApplicationExitInfo.REASON_LOW_MEMORY, "SwipeUpClean"),
        )
    }
}
