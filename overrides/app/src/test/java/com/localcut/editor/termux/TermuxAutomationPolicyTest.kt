package com.localcut.editor.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxAutomationPolicyTest {
    @Test
    fun maintenanceRequiresPairingAndPreviouslyConfirmedManagedAutomation() {
        assertFalse(TermuxAutomationPolicy.needsMaintenance(false, false, false, emptySet()))
        assertFalse(TermuxAutomationPolicy.needsMaintenance(true, false, false, emptySet()))
        assertTrue(TermuxAutomationPolicy.needsMaintenance(true, true, false, emptySet()))
        assertTrue(
            TermuxAutomationPolicy.needsMaintenance(
                paired = true,
                managedReady = true,
                connected = true,
                capabilities = setOf("FFPROBE", "FFMPEG"),
            ),
        )
        assertFalse(
            TermuxAutomationPolicy.needsMaintenance(
                paired = true,
                managedReady = true,
                connected = true,
                capabilities = TermuxAutomationPolicy.REQUIRED_CREATIVE_CAPABILITIES,
            ),
        )
    }

    @Test
    fun fullCreativeCapabilitiesAreExplicitlyRecognized() {
        assertFalse(TermuxAutomationPolicy.hasFullCreativeCapabilities(setOf("FFPROBE")))
        assertTrue(TermuxAutomationPolicy.hasFullCreativeCapabilities(TermuxAutomationPolicy.REQUIRED_CREATIVE_CAPABILITIES))
    }

    @Test
    fun managedAutomationCapabilityIsExplicit() {
        assertFalse(TermuxAutomationPolicy.hasManagedAutomationCapability(emptySet()))
        assertTrue(
            TermuxAutomationPolicy.hasManagedAutomationCapability(
                setOf(TermuxAutomationPolicy.MANAGED_AUTOMATION_CAPABILITY),
            ),
        )
    }

    @Test
    fun wakeAttemptsAreThrottledForOneMinute() {
        assertTrue(TermuxAutomationPolicy.shouldAttemptWake(0L, 1L))
        assertFalse(TermuxAutomationPolicy.shouldAttemptWake(1_000L, 60_999L))
        assertTrue(TermuxAutomationPolicy.shouldAttemptWake(1_000L, 61_000L))
    }
}
