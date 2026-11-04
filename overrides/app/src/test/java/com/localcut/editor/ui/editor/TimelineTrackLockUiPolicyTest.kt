package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTrackLockUiPolicyTest {
    @Test
    fun toggleInvertsCurrentLockState() {
        assertTrue(TimelineTrackLockUiPolicy.nextLocked(false))
        assertFalse(TimelineTrackLockUiPolicy.nextLocked(true))
    }

    @Test
    fun labelMakesLockedStateVisibleWithoutChangingOtherTrackControls() {
        assertEquals("L", TimelineTrackLockUiPolicy.label(false))
        assertEquals("L✓", TimelineTrackLockUiPolicy.label(true))
    }

    @Test
    fun lockedTrackDisablesClipMutationGestures() {
        assertTrue(TimelineTrackLockUiPolicy.allowsClipEdits(locked = false))
        assertFalse(TimelineTrackLockUiPolicy.allowsClipEdits(locked = true))
    }
}
