package com.localcut.editor.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineRecoveryPolicyTest {
    @Test
    fun pairedBootstrapUpdatesOnlyManagedSourceAndEnablesAutomation() {
        val command = TermuxEngineRecoveryPolicy.bootstrapCommand(paired = true)
        assertTrue(command.contains("pkg install -y git python ffmpeg"))
        assertTrue(command.contains(TermuxEngineRecoveryPolicy.REPOSITORY_URL))
        assertTrue(command.contains(TermuxEngineRecoveryPolicy.DEVELOPMENT_BRANCH))
        assertTrue(command.contains("termux-engine-ci.yml/runs"))
        assertTrue(command.contains("status=success"))
        assertTrue(command.contains("merge-base --is-ancestor HEAD"))
        assertTrue(command.contains("bootstrap_termux.sh"))
        assertSafe(command)
    }

    @Test
    fun connectedEngineMissingCreativeCapabilitiesOffersMigration() {
        assertTrue(
            TermuxEngineRecoveryPolicy.shouldOfferRepair(
                paired = true,
                connected = true,
                errorCode = null,
                managedReady = false,
                capabilities = setOf("FFPROBE", "ANALYZE_MEDIA"),
            ),
        )
    }

    @Test
    fun confirmedManagedEngineWithFullCreativeCapabilitiesNeedsNoManualRepair() {
        assertFalse(
            TermuxEngineRecoveryPolicy.shouldOfferRepair(
                paired = true,
                connected = true,
                errorCode = null,
                managedReady = true,
                capabilities = TermuxAutomationPolicy.REQUIRED_CREATIVE_CAPABILITIES +
                    TermuxAutomationPolicy.MANAGED_AUTOMATION_CAPABILITY,
            ),
        )
    }

    @Test
    fun pairedOfflineEngineWithoutManagedEvidenceOffersBootstrap() {
        assertTrue(
            TermuxEngineRecoveryPolicy.shouldOfferRepair(
                paired = true,
                connected = false,
                errorCode = null,
                managedReady = false,
                capabilities = emptySet(),
            ),
        )
    }

    @Test
    fun unpairedBootstrapPreparesAutomationBeforePairing() {
        val command = TermuxEngineRecoveryPolicy.bootstrapCommand(paired = false)
        assertTrue(command.contains("setup_termux.sh --enable-external-automation"))
        assertTrue(command.endsWith("python termux-engine/localcut_supervisor.py --pair"))
        assertSafe(command)
    }

    private fun assertSafe(command: String) {
        assertFalse(command.contains("LOCALCUT_ENGINE_TOKEN"))
        assertFalse(command.contains("rm -"))
        assertFalse(command.contains("force"))
        assertFalse(command.contains("reset --hard"))
        assertFalse(command.contains("pkill"))
        assertFalse(command.contains("kill -9"))
    }
}
