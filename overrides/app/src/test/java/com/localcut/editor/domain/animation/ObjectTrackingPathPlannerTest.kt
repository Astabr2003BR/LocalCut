package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingPathPlannerTest {
    @Test
    fun mapsNormalizedDisplacementIntoEditorSpaceAndHoldsLowConfidenceSamples() {
        val animations = ObjectTrackingPathPlanner.plan(
            samples = listOf(
                ObjectTrackingPositionSample(100_000L, dxNorm = 0.10, dyNorm = -0.05, confidence = 0.9),
                ObjectTrackingPositionSample(200_000L, dxNorm = 0.90, dyNorm = 0.90, confidence = 0.1),
                ObjectTrackingPositionSample(300_000L, dxNorm = 0.20, dyNorm = 0.10, confidence = 0.8),
            ),
            renderWidthPx = 1080.0,
            renderHeightPx = 1920.0,
            baseX = 20.0,
            baseY = -10.0,
            minConfidence = 0.35,
        )

        assertEquals(listOf("transform.x", "transform.y"), animations.map { it.propertyId })
        val x = animations.first { it.propertyId == "transform.x" }.keyframes.map {
            (it.value as PropertyValue.Scalar).value
        }
        val y = animations.first { it.propertyId == "transform.y" }.keyframes.map {
            (it.value as PropertyValue.Scalar).value
        }
        assertEquals(listOf(20.0, 128.0, 128.0, 236.0), x)
        assertEquals(listOf(-10.0, -106.0, -106.0, 182.0), y)
        assertTrue(animations.all { animation -> animation.keyframes.all { it.id.startsWith("object-track-") } })
    }

    @Test
    fun sourceGeometryUsesSameInitialFitAsMedia3Presentation() {
        val animations = ObjectTrackingPathPlanner.planFromSourceGeometry(
            samples = listOf(
                ObjectTrackingPositionSample(100_000L, dxNorm = 0.10, dyNorm = 0.10, confidence = 1.0),
            ),
            sourceWidthPx = 1200,
            sourceHeightPx = 900,
            outputWidthPx = 1600,
            outputHeightPx = 900,
            transformSpaceWidthPx = 1600.0,
            transformSpaceHeightPx = 900.0,
            baseX = 0.0,
            baseY = 0.0,
        )

        val x = animations.single { it.propertyId == "transform.x" }.keyframes.map {
            (it.value as PropertyValue.Scalar).value
        }
        val y = animations.single { it.propertyId == "transform.y" }.keyframes.map {
            (it.value as PropertyValue.Scalar).value
        }

        // 4:3 source is pillarboxed to 75% of a 16:9 output before the clip transform.
        assertEquals(listOf(0.0, 120.0), x)
        assertEquals(listOf(0.0, 90.0), y)
    }

    @Test
    fun sourceGeometryHandlesLetterboxedWideSource() {
        val animations = ObjectTrackingPathPlanner.planFromSourceGeometry(
            samples = listOf(ObjectTrackingPositionSample(100_000L, 0.10, 0.10, 1.0)),
            sourceWidthPx = 1600,
            sourceHeightPx = 900,
            outputWidthPx = 900,
            outputHeightPx = 900,
            transformSpaceWidthPx = 900.0,
            transformSpaceHeightPx = 900.0,
            baseX = 0.0,
            baseY = 0.0,
        )

        val x = animations.single { it.propertyId == "transform.x" }.keyframes.last().value as PropertyValue.Scalar
        val y = animations.single { it.propertyId == "transform.y" }.keyframes.last().value as PropertyValue.Scalar
        assertEquals(90.0, x.value, 0.0)
        assertEquals(50.625, y.value, 1e-9)
    }

    @Test
    fun rejectsUnorderedNonFiniteOrUnusableTracking() {
        assertTrue(runCatching {
            ObjectTrackingPathPlanner.plan(
                samples = listOf(
                    ObjectTrackingPositionSample(200L, 0.0, 0.0, 1.0),
                    ObjectTrackingPositionSample(100L, 0.0, 0.0, 1.0),
                ),
                renderWidthPx = 100.0,
                renderHeightPx = 100.0,
                baseX = 0.0,
                baseY = 0.0,
            )
        }.isFailure)
        assertTrue(runCatching {
            ObjectTrackingPathPlanner.plan(
                samples = listOf(ObjectTrackingPositionSample(100L, Double.NaN, 0.0, 1.0)),
                renderWidthPx = 100.0,
                renderHeightPx = 100.0,
                baseX = 0.0,
                baseY = 0.0,
            )
        }.isFailure)
        val lowConfidence = runCatching {
            ObjectTrackingPathPlanner.plan(
                samples = listOf(ObjectTrackingPositionSample(100L, 0.1, 0.1, 0.1)),
                renderWidthPx = 100.0,
                renderHeightPx = 100.0,
                baseX = 0.0,
                baseY = 0.0,
                minConfidence = 0.5,
            )
        }
        assertTrue(lowConfidence.isFailure)
        assertEquals("OBJECT_TRACKING_CONFIDENCE_TOO_LOW", lowConfidence.exceptionOrNull()?.message)
    }
}
