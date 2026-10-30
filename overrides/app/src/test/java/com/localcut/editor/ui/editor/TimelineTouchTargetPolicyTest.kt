package com.localcut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTouchTargetPolicyTest {
    @Test
    fun playheadHitAreaMeetsMobileTouchTarget() {
        assertTrue(TimelineTouchTargetPolicy.PLAYHEAD_HIT_WIDTH_DP >= 48f)
    }

    @Test
    fun trimHitAreaIsLargerThanVisibleHandle() {
        assertTrue(TimelineTouchTargetPolicy.TRIM_HIT_WIDTH_DP >= 24f)
        assertTrue(
            TimelineTouchTargetPolicy.TRIM_GLYPH_WIDTH_DP <
                TimelineTouchTargetPolicy.TRIM_HIT_WIDTH_DP,
        )
    }
}
