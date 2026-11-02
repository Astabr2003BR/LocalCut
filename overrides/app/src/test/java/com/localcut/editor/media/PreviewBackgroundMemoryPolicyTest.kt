package com.localcut.editor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBackgroundMemoryPolicyTest {
    @Test
    fun suspendsLoadedPreviewWhenEditorLeavesForeground() {
        assertTrue(
            PreviewBackgroundMemoryPolicy.shouldSuspend(
                previewDurationUs = 4_640_000L,
                changingConfigurations = false,
            ),
        )
    }

    @Test
    fun doesNotSuspendEmptyPreview() {
        assertFalse(
            PreviewBackgroundMemoryPolicy.shouldSuspend(
                previewDurationUs = 0L,
                changingConfigurations = false,
            ),
        )
    }

    @Test
    fun doesNotChurnPlayerDuringConfigurationChange() {
        assertFalse(
            PreviewBackgroundMemoryPolicy.shouldSuspend(
                previewDurationUs = 4_640_000L,
                changingConfigurations = true,
            ),
        )
    }
}
