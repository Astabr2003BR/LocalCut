package com.localcut.editor.media

import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyframeRenderResolverTest {
    @Test
    fun compositionTimestampIsConvertedToClipLocalTime() {
        val keyframes = listOf(
            TransformKeyframe(0L, TransformValue(x = 0.0, scale = 1.0, opacity = 1.0)),
            TransformKeyframe(500_000L, TransformValue(x = 100.0, scale = 2.0, opacity = 0.5)),
        )

        val value = KeyframeRenderResolver.valueAt(
            transform = TransformValue(x = -1.0),
            keyframes = keyframes,
            presentationTimeUs = 1_250_000L,
            presentationStartUs = 1_000_000L,
        )

        assertEquals(50.0, value.x, 0.0001)
        assertEquals(1.5, value.scale, 0.0001)
        assertEquals(0.75, value.opacity, 0.0001)
    }

    @Test
    fun effectiveOpacityMultipliesClipAndAnimatedOpacity() {
        val value = TransformValue(opacity = 0.75)
        assertEquals(0.6, KeyframeRenderResolver.effectiveOpacity(0.8, value), 0.0001)
        assertEquals(0.0, KeyframeRenderResolver.effectiveOpacity(0.0, value), 0.0001)
    }

    @Test
    fun staticTransformStillUsesItsOpacityWhenThereAreNoKeyframes() {
        val transform = TransformValue(x = 12.0, opacity = 0.4)
        val value = KeyframeRenderResolver.valueAt(
            transform = transform,
            keyframes = emptyList(),
            presentationTimeUs = 7_000_000L,
            presentationStartUs = 5_000_000L,
        )
        assertEquals(transform, value)
        assertEquals(0.2, KeyframeRenderResolver.effectiveOpacity(0.5, value), 0.0001)
    }

    @Test
    fun timestampsBeforeItemStartClampToFirstKeyframe() {
        val keyframes = listOf(
            TransformKeyframe(0L, TransformValue(x = 1.0)),
            TransformKeyframe(1_000_000L, TransformValue(x = 9.0)),
        )
        val value = KeyframeRenderResolver.valueAt(
            transform = TransformValue(),
            keyframes = keyframes,
            presentationTimeUs = 900_000L,
            presentationStartUs = 1_000_000L,
        )
        assertEquals(1.0, value.x, 0.0001)
    }
}
