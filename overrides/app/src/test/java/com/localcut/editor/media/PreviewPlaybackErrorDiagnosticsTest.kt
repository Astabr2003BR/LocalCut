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


    @Test fun revealsSafeMedia3OriginWithoutExposingMessagesOrPrivatePaths() {
        val root = IllegalStateException("Bearer secret file:///private/path?token=abcdef")
        root.stackTrace = arrayOf(
            StackTraceElement("androidx.media3.common.util.Assertions", "checkState", "Assertions.java", 85),
            StackTraceElement("androidx.media3.effect.DefaultVideoCompositor", "queueInputTexture", "DefaultVideoCompositor.java", 193),
            StackTraceElement("com.example.Sensitive", "secretMethod", "Account.kt", 42),
        )
        val output = PreviewPlaybackErrorDiagnostics.describe(7001,
            IllegalArgumentException("content://private/image", root))
        assertTrue(output.contains("código 7001"))
        assertTrue(output.contains("DefaultVideoCompositor.queueInputTexture"))
        assertFalse(output.contains("Assertions.checkState"))
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("Bearer"))
        assertFalse(output.contains("content://"))
        assertFalse(output.contains("/private"))
        assertFalse(output.contains("token="))
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
