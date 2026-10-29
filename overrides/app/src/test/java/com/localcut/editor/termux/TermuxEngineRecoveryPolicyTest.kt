package com.localcut.editor.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineRecoveryPolicyTest {
    @Test
    fun pairedRecoveryUsesExistingLauncherAndSafeRepairCommand() {
        val home = "${'$'}HOME"
        val quick = TermuxEngineRecoveryPolicy.quickCommand(paired = true)
        val repair = TermuxEngineRecoveryPolicy.repairCommand(paired = true)

        assertEquals("bash \"$home/.localcut-engine/start-localcut-engine\"", quick)
        assertTrue(repair.contains("pkg install -y git python ffmpeg"))
        assertTrue(repair.contains(TermuxEngineRecoveryPolicy.DEVELOPMENT_BRANCH))
        assertTrue(repair.contains("termux-engine/setup_termux.sh"))
        assertTrue(repair.endsWith(quick))
        assertSafe(quick)
        assertSafe(repair)
    }

    @Test
    fun unpairedRecoveryStartsPairingFromManagedSource() {
        val home = "${'$'}HOME"
        val quick = TermuxEngineRecoveryPolicy.quickCommand(paired = false)
        val repair = TermuxEngineRecoveryPolicy.repairCommand(paired = false)

        assertTrue(quick.contains("$home/.localcut-engine/source"))
        assertTrue(quick.endsWith("python termux-engine/localcut_supervisor.py --pair"))
        assertTrue(repair.contains(TermuxEngineRecoveryPolicy.REPOSITORY_URL))
        assertTrue(repair.endsWith(quick))
        assertSafe(quick)
        assertSafe(repair)
    }

    private fun assertSafe(command: String) {
        assertFalse(command.contains("LOCALCUT_ENGINE_TOKEN"))
        assertFalse(command.contains("rm -"))
        assertFalse(command.contains("force"))
        assertFalse(command.contains("reset --hard"))
    }
}
