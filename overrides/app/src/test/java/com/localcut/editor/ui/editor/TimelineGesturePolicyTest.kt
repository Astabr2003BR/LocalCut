package com.localcut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGesturePolicyTest {
    @Test
    fun horizontalScrollIsEnabledWhenNoExclusiveHandleIsActive() {
        assertTrue(TimelineGesturePolicy.isHorizontalScrollEnabled(exclusiveHandleActive = false))
    }

    @Test
    fun horizontalScrollIsDisabledWhilePlayheadOrTrimOwnsGesture() {
        assertFalse(TimelineGesturePolicy.isHorizontalScrollEnabled(exclusiveHandleActive = true))
    }
}
