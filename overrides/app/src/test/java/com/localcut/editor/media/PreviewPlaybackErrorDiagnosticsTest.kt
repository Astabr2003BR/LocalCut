package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlaybackErrorDiagnosticsTest {
    @Test fun reportsCodeAndSafeCauseClasses() {
        val cause = IllegalArgumentException("Bearer secret content://private?token=x")
        val failure = IllegalStateException("file:///data/user/private.mp3", cause)
        val output = PreviewPlaybackErrorDiagnostics.describe(4003, failure)
        assertTrue(output.contains("código 4003"))
        assertTrue(output.contains("IllegalStateException > IllegalArgumentException"))
        assertFalse(output.contains("Bearer"))
        assertFalse(output.contains("content://"))
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("/data/user"))
    }

    @Test fun limitsDeepCausesAndSupportsNoCause() {
        assertTrue(PreviewPlaybackErrorDiagnostics.describe(1000, RuntimeException())
            .contains("RuntimeException"))
        var error: Throwable = IllegalArgumentException("secret")
        repeat(10) { error = IllegalStateException("secret", error) }
        val output = PreviewPlaybackErrorDiagnostics.describe(4003, error)
        assertTrue(output.split(" > ").size <= 5)
        assertFalse(output.contains("secret"))
    }
}
