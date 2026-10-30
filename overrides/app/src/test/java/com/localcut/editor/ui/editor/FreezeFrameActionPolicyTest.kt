package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeFrameActionPolicyTest {
    @Test
    fun simpleForwardClipInsideBoundsIsAllowed() {
        assertTrue(
            FreezeFrameActionPolicy.canCreate(
                timeMap = TimeMap(),
                fadeInUs = 0L,
                fadeOutUs = 0L,
                presentationOffsetUs = 1_000_000L,
                clipDurationUs = 4_000_000L,
            ),
        )
    }

    @Test
    fun audioFadesAreBlockedToMatchMedia3ExportContract() {
        assertFalse(
            FreezeFrameActionPolicy.canCreate(
                timeMap = TimeMap(),
                fadeInUs = 500_000L,
                fadeOutUs = 0L,
                presentationOffsetUs = 1_000_000L,
                clipDurationUs = 4_000_000L,
            ),
        )
        assertFalse(
            FreezeFrameActionPolicy.canCreate(
                timeMap = TimeMap(),
                fadeInUs = 0L,
                fadeOutUs = 500_000L,
                presentationOffsetUs = 1_000_000L,
                clipDurationUs = 4_000_000L,
            ),
        )
    }

    @Test
    fun unsupportedTimeMapsRemainBlocked() {
        assertFalse(
            FreezeFrameActionPolicy.canCreate(
                timeMap = TimeMap(reverse = true),
                fadeInUs = 0L,
                fadeOutUs = 0L,
                presentationOffsetUs = 1_000_000L,
                clipDurationUs = 4_000_000L,
            ),
        )
        assertFalse(
            FreezeFrameActionPolicy.canCreate(
                timeMap = TimeMap(speedPoints = listOf(SpeedPoint(0L, 1.0))),
                fadeInUs = 0L,
                fadeOutUs = 0L,
                presentationOffsetUs = 1_000_000L,
                clipDurationUs = 4_000_000L,
            ),
        )
        assertFalse(
            FreezeFrameActionPolicy.canCreate(
                timeMap = TimeMap(freezeSegments = listOf(FreezeSegment(1_000_000L, 500_000L, 1_000_000L))),
                fadeInUs = 0L,
                fadeOutUs = 0L,
                presentationOffsetUs = 1_000_000L,
                clipDurationUs = 4_000_000L,
            ),
        )
        assertFalse(
            FreezeFrameActionPolicy.canCreate(
                timeMap = TimeMap(baseSpeed = Double.NaN),
                fadeInUs = 0L,
                fadeOutUs = 0L,
                presentationOffsetUs = 1_000_000L,
                clipDurationUs = 4_000_000L,
            ),
        )
    }

    @Test
    fun playheadMustStayInsidePositiveDurationClip() {
        assertFalse(
            FreezeFrameActionPolicy.canCreate(TimeMap(), 0L, 0L, -1L, 4_000_000L),
        )
        assertFalse(
            FreezeFrameActionPolicy.canCreate(TimeMap(), 0L, 0L, 4_000_000L, 4_000_000L),
        )
        assertFalse(
            FreezeFrameActionPolicy.canCreate(TimeMap(), 0L, 0L, 0L, 0L),
        )
    }

    @Test
    fun blockedAudioFadeExplainsHowToRecover() {
        val reason = FreezeFrameActionPolicy.unavailableReason(
            timeMap = TimeMap(),
            fadeInUs = 500_000L,
            fadeOutUs = 0L,
            presentationOffsetUs = 1_000_000L,
            clipDurationUs = 4_000_000L,
        )

        assertTrue(reason.contains("fade de áudio"))
        assertTrue(reason.contains("Remova o fade"))
    }
}
