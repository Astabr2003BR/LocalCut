package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.TextAnimationKind
import com.localcut.editor.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Test

class TextAdvancedAnimationUiPolicyTest {
    @Test
    fun textAnimationTimingAndIntensityControlsStayInsideSafeRanges() {
        val model = TextOverlayEditorModel.forEdit(
            TextOverlay("title", 0L, 3_000_000L, "LocalCut"),
        )
            .withEntranceAnimation(TextAnimationKind.SLIDE_UP)
            .withExitAnimation(TextAnimationKind.FADE)
            .withLoopAnimation(TextAnimationKind.WIGGLE)
            .withEntranceDurationUs(9_000_000L)
            .withExitDurationUs(25_000L)
            .withLoopDurationUs(8_000_000L)
            .withEntranceAmount(5.0)
            .withExitAmount(-2.0)
            .withLoopAmount(1.75)
            .withPerCharacterDelayUs(2_000_000L)

        assertEquals(5_000_000L, model.draft.entrance.durationUs)
        assertEquals(50_000L, model.draft.exit.durationUs)
        assertEquals(5_000_000L, model.draft.loop.durationUs)
        assertEquals(3.0, model.draft.entrance.amount, 0.0)
        assertEquals(0.0, model.draft.exit.amount, 0.0)
        assertEquals(1.75, model.draft.loop.amount, 0.0)
        assertEquals(1_000_000L, model.draft.perCharacterDelayUs)
    }

    @Test
    fun typewriterDelayIsPreservedWhenSwitchingToTypewriterEntrance() {
        val model = TextOverlayEditorModel.forEdit(
            TextOverlay("title", 0L, 2_000_000L, "ABCDE"),
        )
            .withPerCharacterDelayUs(200_000L)
            .withEntranceAnimation(TextAnimationKind.TYPEWRITER)
            .withEntranceDurationUs(1_000_000L)

        assertEquals(TextAnimationKind.TYPEWRITER, model.draft.entrance.kind)
        assertEquals(200_000L, model.draft.perCharacterDelayUs)
        assertEquals(1_000_000L, model.draft.entrance.durationUs)
    }
}
