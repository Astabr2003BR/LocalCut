package com.localcut.editor.diagnostics

import android.app.ApplicationExitInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitDiagnosticsPolicyCrashAssociationTest {
    @Test
    fun currentCrashCanIncludeMatchingStoredException() {
        assertTrue(
            ProcessExitDiagnosticsPolicy.shouldAttachStoredCrash(
                exitReason = ApplicationExitInfo.REASON_CRASH,
                exitTimestampMs = 100_000L,
                crashTimestampMs = 99_000L,
            ),
        )
    }

    @Test
    fun swipeUpOtherExitMustNotIncludeOldCrash() {
        assertFalse(
            ProcessExitDiagnosticsPolicy.shouldAttachStoredCrash(
                exitReason = ApplicationExitInfo.REASON_OTHER,
                exitTimestampMs = 200_000L,
                crashTimestampMs = 100_000L,
            ),
        )
    }

    @Test
    fun staleCrashMustNotBeAttachedToNewCrashEvent() {
        assertFalse(
            ProcessExitDiagnosticsPolicy.shouldAttachStoredCrash(
                exitReason = ApplicationExitInfo.REASON_CRASH,
                exitTimestampMs = 10 * 60_000L,
                crashTimestampMs = 1_000L,
            ),
        )
    }
}
