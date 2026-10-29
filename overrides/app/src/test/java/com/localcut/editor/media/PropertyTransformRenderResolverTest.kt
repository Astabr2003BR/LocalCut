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

class PropertyTransformRenderResolverTest {
    @Test
    fun propertyAnimationsOverrideOnlyDefinedLegacyTransformProperties() {
        val base = TransformValue(
            x = 10.0,
            y = 20.0,
            scale = 1.2,
            rotationDeg = 5.0,
            opacity = 0.8,
        )
        val animations = listOf(
            scalarAnimation("transform.x", 0.0, 100.0),
            scalarAnimation("transform.opacity", 1.0, 0.0),
        )

        val value = PropertyTransformRenderResolver.valueAt(
            transform = base,
            keyframes = emptyList(),
            propertyAnimations = animations,
            presentationTimeUs = 750_000L,
            presentationStartUs = 250_000L,
        )

        assertEquals(50.0, value.x, 1e-9)
        assertEquals(20.0, value.y, 1e-9)
        assertEquals(1.2, value.scale, 1e-9)
        assertEquals(5.0, value.rotationDeg, 1e-9)
        assertEquals(0.5, value.opacity, 1e-9)
    }

    @Test
    fun compositionContainerIsAppliedAfterModernLocalTransformAnimation() {
        val container = CompositionTransformContainer(
            x = 100.0,
            y = 50.0,
            scale = 2.0,
            rotationDeg = 90.0,
        )
        // Local (10,20,1x,5deg) already flattened through the container.
        val flattenedBase = TransformValue(
            x = 60.0,
            y = 70.0,
            scale = 2.0,
            rotationDeg = 95.0,
            opacity = 0.8,
        )
        val xAnimation = scalarAnimation("transform.x", 10.0, 20.0)

        val value = PropertyTransformRenderResolver.valueAt(
            transform = flattenedBase,
            keyframes = emptyList(),
            propertyAnimations = listOf(xAnimation),
            presentationTimeUs = 1_000_000L,
            presentationStartUs = 0L,
            compositionContainer = container,
        )

        // The local +10px X movement is scaled by 2 and rotated by the parent into +20px world Y.
        assertEquals(60.0, value.x, 1e-9)
        assertEquals(90.0, value.y, 1e-9)
        assertEquals(2.0, value.scale, 1e-9)
        assertEquals(95.0, value.rotationDeg, 1e-9)
        assertEquals(0.8, value.opacity, 1e-9)
    }

    @Test
    fun emptyOrUnrelatedPropertyAnimationsKeepLegacyValue() {
        val base = TransformValue(x = 7.0, y = -3.0, scale = 1.1, rotationDeg = 4.0, opacity = 0.7)
        val value = PropertyTransformRenderResolver.valueAt(
            transform = base,
            keyframes = emptyList(),
            propertyAnimations = listOf(
                PropertyAnimation(propertyId = "transform.x", keyframes = emptyList()),
                scalarAnimation("effect.blur", 0.0, 1.0),
            ),
            presentationTimeUs = 500_000L,
            presentationStartUs = 0L,
        )

        assertEquals(base, value)
    }

    @Test
    fun duplicateModernTransformPropertyFailsClosed() {
        val duplicate = listOf(
            scalarAnimation("transform.scale", 1.0, 1.1),
            scalarAnimation("transform.scale", 1.0, 1.2),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PropertyTransformRenderResolver.valueAt(
                transform = TransformValue(),
                keyframes = emptyList(),
                propertyAnimations = duplicate,
                presentationTimeUs = 0L,
                presentationStartUs = 0L,
            )
        }
    }

    @Test
    fun propertyAnimationClockIsClipLocalAndClampedBeforeStart() {
        val animation = scalarAnimation("transform.x", 25.0, 125.0)

        val before = PropertyTransformRenderResolver.valueAt(
            transform = TransformValue(),
            keyframes = emptyList(),
            propertyAnimations = listOf(animation),
            presentationTimeUs = 100_000L,
            presentationStartUs = 250_000L,
        )
        val middle = PropertyTransformRenderResolver.valueAt(
            transform = TransformValue(),
            keyframes = emptyList(),
            propertyAnimations = listOf(animation),
            presentationTimeUs = 750_000L,
            presentationStartUs = 250_000L,
        )

        assertEquals(25.0, before.x, 1e-9)
        assertEquals(75.0, middle.x, 1e-9)
    }

    private fun scalarAnimation(propertyId: String, from: Double, to: Double) = PropertyAnimation(
        propertyId = propertyId,
        keyframes = listOf(
            PropertyKeyframe(
                id = "$propertyId-start",
                offsetUs = 0L,
                value = PropertyValue.Scalar(from),
                easingToNext = Easing(EasingKind.LINEAR),
            ),
            PropertyKeyframe(
                id = "$propertyId-end",
                offsetUs = 1_000_000L,
                value = PropertyValue.Scalar(to),
                easingToNext = Easing(EasingKind.LINEAR),
            ),
        ),
    )
}
