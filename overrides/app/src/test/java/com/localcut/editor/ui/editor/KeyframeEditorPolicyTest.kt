package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyframeEditorPolicyTest {
    private fun clip(
        keyframes: List<TransformKeyframe> = emptyList(),
        transform: TransformValue = TransformValue(x = 3.0, y = 4.0, scale = 1.2, rotationDeg = 5.0, opacity = 0.9),
    ) = Clip(
        id = "clip",
        assetId = "asset",
        startUs = 2_000_000L,
        durationUs = 4_000_000L,
        sourceInUs = 0L,
        sourceOutUs = 4_000_000L,
        transform = transform,
        keyframes = keyframes,
    )

    @Test
    fun projectPlayheadConvertsToInclusiveClipLocalOffset() {
        val clip = clip()
        assertEquals(0L, KeyframeEditorPolicy.localOffsetUs(clip, 2_000_000L))
        assertEquals(1_250_000L, KeyframeEditorPolicy.localOffsetUs(clip, 3_250_000L))
        assertEquals(4_000_000L, KeyframeEditorPolicy.localOffsetUs(clip, 6_000_000L))
        assertNull(KeyframeEditorPolicy.localOffsetUs(clip, 1_999_999L))
        assertNull(KeyframeEditorPolicy.localOffsetUs(clip, 6_000_001L))
    }

    @Test
    fun upsertAtPlayheadInsertsSortedAndReplacesExactOffset() {
        val first = TransformKeyframe(500_000L, TransformValue(x = 5.0))
        val last = TransformKeyframe(3_000_000L, TransformValue(x = 30.0))
        val clip = clip(listOf(last, first))

        val inserted = KeyframeEditorPolicy.upsertAtProjectTime(
            clip,
            projectTimeUs = 3_000_000L,
            value = TransformValue(x = 10.0),
        )!!
        assertEquals(listOf(500_000L, 1_000_000L, 3_000_000L), inserted.map { it.offsetUs })
        assertEquals(10.0, inserted[1].value.x, 0.0001)

        val replaced = KeyframeEditorPolicy.upsertAtProjectTime(
            clip.copy(keyframes = inserted),
            projectTimeUs = 3_000_000L,
            value = TransformValue(x = 99.0),
        )!!
        assertEquals(3, replaced.size)
        assertEquals(99.0, replaced.single { it.offsetUs == 1_000_000L }.value.x, 0.0001)
    }

    @Test
    fun draftValueInterpolatesAnimationOrFallsBackToStaticTransform() {
        val animated = clip(
            keyframes = listOf(
                TransformKeyframe(0L, TransformValue(x = 0.0, scale = 1.0, opacity = 1.0)),
                TransformKeyframe(2_000_000L, TransformValue(x = 20.0, scale = 2.0, opacity = 0.5)),
            ),
        )
        val atOneSecond = KeyframeEditorPolicy.valueAtProjectTime(animated, 3_000_000L)!!
        assertEquals(10.0, atOneSecond.x, 0.0001)
        assertEquals(1.5, atOneSecond.scale, 0.0001)
        assertEquals(0.75, atOneSecond.opacity, 0.0001)

        val staticClip = clip()
        assertEquals(staticClip.transform, KeyframeEditorPolicy.valueAtProjectTime(staticClip, 4_000_000L))
        assertNull(KeyframeEditorPolicy.valueAtProjectTime(staticClip, 7_000_000L))
    }

    @Test
    fun deleteAndNavigationUseExactOffsetsAndProjectTimes() {
        val clip = clip(
            listOf(
                TransformKeyframe(500_000L, TransformValue(x = 5.0)),
                TransformKeyframe(1_500_000L, TransformValue(x = 15.0)),
                TransformKeyframe(3_500_000L, TransformValue(x = 35.0)),
            ),
        )

        assertEquals(3_500_000L, KeyframeEditorPolicy.previousProjectTimeUs(clip, 4_000_000L))
        assertEquals(5_500_000L, KeyframeEditorPolicy.nextProjectTimeUs(clip, 4_000_000L))
        assertNull(KeyframeEditorPolicy.previousProjectTimeUs(clip, 2_500_000L))
        assertNull(KeyframeEditorPolicy.nextProjectTimeUs(clip, 5_500_000L))

        val deleted = KeyframeEditorPolicy.deleteAtProjectTime(clip, 3_500_000L)!!
        assertEquals(listOf(500_000L, 3_500_000L), deleted.map { it.offsetUs })
        assertEquals(clip.keyframes, KeyframeEditorPolicy.deleteAtProjectTime(clip, 3_500_001L))
        assertNull(KeyframeEditorPolicy.deleteAtProjectTime(clip, 7_000_000L))
    }

    @Test
    fun markerFractionsAreNormalizedClampedAndSorted() {
        val clip = clip(
            listOf(
                TransformKeyframe(4_000_000L, TransformValue()),
                TransformKeyframe(2_000_000L, TransformValue()),
                TransformKeyframe(0L, TransformValue()),
                TransformKeyframe(5_000_000L, TransformValue()),
            ),
        )
        assertEquals(
            listOf(0f, 0.5f, 1f, 1f),
            KeyframeEditorPolicy.markerFractions(clip),
        )
        assertEquals(emptyList<Float>(), KeyframeEditorPolicy.markerFractions(clip.copy(durationUs = 0L)))
    }
}
