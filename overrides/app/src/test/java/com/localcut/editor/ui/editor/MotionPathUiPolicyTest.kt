package com.localcut.editor.ui.editor

import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPathUiPolicyTest {
    @Test
    fun gestureBecomesRelativeProjectMotionStartingAtCurrentTransform() {
        val points = MotionPathUiPolicy.toMotionPoints(
            gesture = listOf(
                CanvasPointPx(20.0, 30.0),
                CanvasPointPx(120.0, 80.0),
                CanvasPointPx(220.0, 130.0),
            ),
            surfaceWidthPx = 200.0,
            surfaceHeightPx = 100.0,
            projectWidthPx = 1000.0,
            projectHeightPx = 500.0,
            durationUs = 2_000_000L,
            baseX = 40.0,
            baseY = -20.0,
        )

        assertEquals(3, points.size)
        assertEquals(0L, points[0].offsetUs)
        assertEquals(1_000_000L, points[1].offsetUs)
        assertEquals(2_000_000L, points[2].offsetUs)
        assertEquals(40.0, points[0].x, 0.0)
        assertEquals(-20.0, points[0].y, 0.0)
        assertEquals(540.0, points[1].x, 0.0)
        assertEquals(230.0, points[1].y, 0.0)
        assertEquals(1040.0, points[2].x, 0.0)
        assertEquals(480.0, points[2].y, 0.0)
    }

    @Test
    fun mergeReplacesOnlyXYAndPreservesUnrelatedAnimations() {
        val opacity = scalar("transform.opacity", "opacity-old", 0.5)
        val oldX = scalar("transform.x", "x-old", 99.0)
        val path = listOf(
            scalar("transform.x", "motion-path-x-a", 1.0),
            scalar("transform.y", "motion-path-y-a", 2.0),
        )

        val merged = MotionPathUiPolicy.merge(existing = listOf(opacity, oldX), path = path)

        assertEquals(listOf("transform.opacity", "transform.x", "transform.y"), merged.map { it.propertyId })
        assertEquals(opacity, merged.first())
        assertTrue(merged.drop(1).all { animation -> animation.keyframes.all { it.id.startsWith("motion-path-") } })
    }

    @Test
    fun generatedPathIsDetectedAndRemovedWithoutTouchingOtherProperties() {
        val opacity = scalar("transform.opacity", "opacity-old", 0.5)
        val generated = listOf(
            scalar("transform.x", "motion-path-x-ui-0", 10.0),
            scalar("transform.y", "motion-path-y-ui-0", 20.0),
        )
        val animations = listOf(opacity) + generated

        assertTrue(MotionPathUiPolicy.hasGeneratedMotionPath(animations))
        assertEquals(listOf(opacity), MotionPathUiPolicy.removeGeneratedMotionPath(animations))

        val manual = listOf(
            opacity,
            scalar("transform.x", "manual-x", 10.0),
            scalar("transform.y", "manual-y", 20.0),
        )
        assertFalse(MotionPathUiPolicy.hasGeneratedMotionPath(manual))
        assertEquals(manual, MotionPathUiPolicy.removeGeneratedMotionPath(manual))
    }

    @Test
    fun completeOrPartialStabilizationFailsClosedInsteadOfProducingHybridMotion() {
        val fullStabilization = listOf(
            scalar("transform.x", "stabilize-x-0-0", 0.0),
            scalar("transform.y", "stabilize-y-0-0", 0.0),
            scalar("transform.rotationDeg", "stabilize-rotation-0-0", 0.0),
            scalar("transform.scale", "stabilize-scale-0-0", 1.0),
        )
        val partialStabilization = listOf(
            scalar("transform.rotationDeg", "stabilize-rotation-0-0", 0.0),
        )
        val path = listOf(
            scalar("transform.x", "motion-path-x-a", 1.0),
            scalar("transform.y", "motion-path-y-a", 2.0),
        )

        listOf(fullStabilization, partialStabilization).forEach { existing ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                MotionPathUiPolicy.merge(existing, path)
            }
            assertEquals("MOTION_PATH_CONFLICTS_WITH_STABILIZATION", error.message)
        }
    }

    @Test
    fun invalidDurationOrGestureFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            MotionPathUiPolicy.toMotionPoints(
                gesture = listOf(CanvasPointPx(0.0, 0.0)),
                surfaceWidthPx = 100.0,
                surfaceHeightPx = 100.0,
                projectWidthPx = 1080.0,
                projectHeightPx = 1920.0,
                durationUs = 1_000_000L,
                baseX = 0.0,
                baseY = 0.0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MotionPathUiPolicy.toMotionPoints(
                gesture = listOf(CanvasPointPx(0.0, 0.0), CanvasPointPx(10.0, 10.0)),
                surfaceWidthPx = 100.0,
                surfaceHeightPx = 100.0,
                projectWidthPx = 1080.0,
                projectHeightPx = 1920.0,
                durationUs = 600_000_001L,
                baseX = 0.0,
                baseY = 0.0,
            )
        }
    }

    private fun scalar(propertyId: String, keyId: String, value: Double): PropertyAnimation = PropertyAnimation(
        propertyId = propertyId,
        keyframes = listOf(
            PropertyKeyframe(
                id = keyId,
                offsetUs = 0L,
                value = PropertyValue.Scalar(value),
                easingToNext = Easing(),
            ),
        ),
    )
}
