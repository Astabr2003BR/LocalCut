package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBackgroundSessionTest {
    @Test
    fun capturesPositionOnlyOnceUntilResumeConsumesIt() {
        val session = PreviewBackgroundSession()

        assertTrue(session.beginSuspend(2_300_000L))
        assertFalse(session.beginSuspend(3_100_000L))
        assertTrue(session.isSuspended)
        assertEquals(2_300_000L, session.takeResumePositionUs())
        assertFalse(session.isSuspended)
        assertNull(session.takeResumePositionUs())
    }

    @Test
    fun mobileHandoffSeekWhileSuspendedReplacesSavedPositionWithoutUnsuspending() {
        val session = PreviewBackgroundSession()
        assertFalse(session.updateSuspendedPositionUs(1_500_000L))
        assertTrue(session.beginSuspend(0L))
        assertTrue(session.updateSuspendedPositionUs(1_500_000L))
        assertTrue(session.isSuspended)
        assertEquals(1_500_000L, session.takeResumePositionUs())
        assertFalse(session.updateSuspendedPositionUs(900_000L))
        assertNull(session.takeResumePositionUs())
    }

    @Test
    fun clampsNegativePositionBeforeSaving() {
        val session = PreviewBackgroundSession()

        assertTrue(session.beginSuspend(-5L))
        assertEquals(0L, session.takeResumePositionUs())
    }
}
