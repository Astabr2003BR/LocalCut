package com.localcut.editor.termux

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxRunCommandSpecTest {
    @Test
    fun automaticWakeUsesStaticBackgroundCommandOnly() {
        assertEquals("com.termux.permission.RUN_COMMAND", TermuxRunCommandSpec.PERMISSION)
        assertEquals("com.termux.RUN_COMMAND", TermuxRunCommandSpec.ACTION)
        assertEquals("com.termux.app.RunCommandService", TermuxRunCommandSpec.SERVICE_CLASS)
        assertEquals("/data/data/com.termux/files/usr/bin/bash", TermuxRunCommandSpec.BASH_PATH)
        assertArrayEquals(
            arrayOf("/data/data/com.termux/files/home/.localcut-engine/auto-recover", "--once"),
            TermuxRunCommandSpec.arguments(),
        )
    }
}
