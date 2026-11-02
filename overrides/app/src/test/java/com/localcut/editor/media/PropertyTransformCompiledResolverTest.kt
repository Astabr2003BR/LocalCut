package com.localcut.editor.media

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PropertyTransformCompiledResolverTest {
    @Test
    fun compiledResolverMatchesDirectEvaluationAcrossFrames() {
        val animation = PropertyAnimation(
            propertyId = "transform.x",
            keyframes = listOf(
                PropertyKeyframe(
                    id = "x-0",
                    offsetUs = 0L,
                    value = PropertyValue.Scalar(10.0),
                    easingToNext = Easing(EasingKind.LINEAR),
                ),
                PropertyKeyframe(
                    id = "x-1",
                    offsetUs = 1_000_000L,
                    value = PropertyValue.Scalar(110.0),
                ),
            ),
        )
        val compiled = PropertyTransformRenderResolver.compile(
            transform = TransformValue(x = 10.0, y = 20.0),
            keyframes = emptyList(),
            propertyAnimations = listOf(animation),
            presentationStartUs = 250_000L,
        )

        assertEquals(10.0, compiled.valueAt(100_000L).x, 1e-9)
        assertEquals(60.0, compiled.valueAt(750_000L).x, 1e-9)
        assertEquals(110.0, compiled.valueAt(1_250_000L).x, 1e-9)
        assertEquals(20.0, compiled.valueAt(750_000L).y, 1e-9)
    }

    @Test
    fun compileRejectsInvalidAnimationBeforeAnyFrameEvaluation() {
        val invalid = PropertyAnimation(
            propertyId = "transform.x",
            keyframes = listOf(
                PropertyKeyframe("later", 1_000L, PropertyValue.Scalar(1.0)),
                PropertyKeyframe("earlier", 500L, PropertyValue.Scalar(0.0)),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PropertyTransformRenderResolver.compile(
                transform = TransformValue(),
                keyframes = emptyList(),
                propertyAnimations = listOf(invalid),
                presentationStartUs = 0L,
            )
        }
    }
}
