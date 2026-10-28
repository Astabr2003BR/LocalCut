package com.localcut.editor.analysis

import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilizationApplyPolicyTest {
    private fun animation(propertyId: String, value: Double) = PropertyAnimation(
        propertyId = propertyId,
        keyframes = listOf(
            PropertyKeyframe("$propertyId-0", 0L, PropertyValue.Scalar(value)),
        ),
    )

    @Test
    fun replacesOnlyStabilizedTransformPropertiesAndPreservesOthers() {
        val existing = listOf(
            animation("transform.x", 99.0),
            animation("transform.opacity", 0.5),
            animation("effect.glow.amount", 2.0),
        )
        val stabilization = listOf(
            animation("transform.x", 0.0),
            animation("transform.y", 0.0),
            animation("transform.rotationDeg", 0.0),
            animation("transform.scale", 1.0),
        )

        val merged = StabilizationApplyPolicy.merge(existing, stabilization)

        assertEquals(
            listOf(
                "transform.opacity",
                "effect.glow.amount",
                "transform.x",
                "transform.y",
                "transform.rotationDeg",
                "transform.scale",
            ),
            merged.map { it.propertyId },
        )
        assertEquals(0.5, ((merged.first { it.propertyId == "transform.opacity" }.keyframes.single().value) as PropertyValue.Scalar).value, 0.0)
    }

    @Test
    fun rejectsDuplicateOrUnexpectedStabilizationProperties() {
        val duplicate = listOf(animation("transform.x", 0.0), animation("transform.x", 1.0))
        val unexpected = listOf(animation("transform.opacity", 1.0))

        assertTrue(runCatching { StabilizationApplyPolicy.merge(emptyList(), duplicate) }.isFailure)
        assertTrue(runCatching { StabilizationApplyPolicy.merge(emptyList(), unexpected) }.isFailure)
    }
}
