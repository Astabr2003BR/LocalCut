package com.localcut.editor.analysis

import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisModelTest {
    @Test
    fun trackingSamplesMustBeOrderedBoundedAndFinite() {
        val valid = TrackingData(
            target = TrackingTarget(TrackingTargetKind.OBJECT, normalizedX = 0.5, normalizedY = 0.5),
            samples = listOf(
                TrackingSample(0L, 0.4, 0.5, 1.0, 0.0, 0.9),
                TrackingSample(500_000L, 0.6, 0.5, 1.1, 5.0, 0.8),
            ),
        )
        assertTrue(valid.validationIssues().isEmpty())
        assertTrue(valid.copy(samples = valid.samples.reversed()).validationIssues().isNotEmpty())
        assertTrue(valid.copy(samples = listOf(valid.samples.first().copy(normalizedX = 2.0))).validationIssues().isNotEmpty())
    }

    @Test
    fun trackingAdapterProducesOrdinaryPropertyAnimations() {
        val data = TrackingData(
            target = TrackingTarget(TrackingTargetKind.OBJECT, 0.5, 0.5),
            samples = listOf(
                TrackingSample(0L, 0.25, 0.5, 1.0, 0.0, 1.0),
                TrackingSample(1_000_000L, 0.75, 0.6, 1.2, 10.0, 1.0),
            ),
        )

        val animations = TrackingAnimationAdapter.toPropertyAnimations(data, projectWidth = 1000, projectHeight = 500)

        val x = animations.first { it.propertyId == "transform.x" }
        assertEquals(PropertyValue.Scalar(-250.0), x.keyframes.first().value)
        assertEquals(PropertyValue.Scalar(250.0), x.keyframes.last().value)
        val y = animations.first { it.propertyId == "transform.y" }
        assertEquals(PropertyValue.Scalar(50.0), y.keyframes.last().value)
    }

    @Test
    fun stabilizationCorrectionsAreInverseCameraMotion() {
        val data = StabilizationData(
            samples = listOf(
                StabilizationSample(0L, motionX = 10.0, motionY = -5.0, rotationDeg = 2.0, zoom = 1.02),
            ),
        )

        val correction = StabilizationAdapter.correctionAt(data, 0L)

        assertEquals(-10.0, correction.x, 1e-9)
        assertEquals(5.0, correction.y, 1e-9)
        assertEquals(-2.0, correction.rotationDeg, 1e-9)
        assertTrue(correction.scale > 0.0)
    }

    @Test
    fun stabilizationCanBakeIntoOrdinaryPropertyAnimations() {
        val data = StabilizationData(
            samples = listOf(
                StabilizationSample(0L, 10.0, -5.0, 2.0, 1.0),
                StabilizationSample(1_000_000L, 4.0, 1.0, -1.0, 1.02),
            ),
            cropScale = 1.08,
        )

        val animations = StabilizationAdapter.toPropertyAnimations(data)
        assertEquals(PropertyValue.Scalar(-10.0), animations.first { it.propertyId == "transform.x" }.keyframes.first().value)
        assertEquals(PropertyValue.Scalar(5.0), animations.first { it.propertyId == "transform.y" }.keyframes.first().value)
        assertTrue(animations.all { it.keyframes.size == 2 })
    }

    @Test
    fun reframeSamplesRequireOrderedNormalizedCenters() {
        val data = AutoReframeData(
            aspectWidth = 9,
            aspectHeight = 16,
            samples = listOf(
                ReframeSample(0L, 0.5, 0.5, 1.0),
                ReframeSample(1_000_000L, 0.55, 0.45, 1.1),
            ),
        )

        assertTrue(data.validationIssues().isEmpty())
        assertTrue(data.copy(aspectWidth = 0).validationIssues().isNotEmpty())
    }

    @Test
    fun autoReframeCanBakeCenterAndScaleIntoClipAnimations() {
        val data = AutoReframeData(
            aspectWidth = 9,
            aspectHeight = 16,
            samples = listOf(
                ReframeSample(0L, 0.5, 0.5, 1.0),
                ReframeSample(1_000_000L, 0.6, 0.4, 1.2),
            ),
        )

        val animations = AutoReframeAdapter.toPropertyAnimations(data, projectWidth = 1000, projectHeight = 500)
        assertEquals(PropertyValue.Scalar(-100.0), animations.first { it.propertyId == "transform.x" }.keyframes.last().value)
        assertEquals(PropertyValue.Scalar(50.0), animations.first { it.propertyId == "transform.y" }.keyframes.last().value)
        assertEquals(PropertyValue.Scalar(1.2), animations.first { it.propertyId == "transform.scale" }.keyframes.last().value)
    }
}
