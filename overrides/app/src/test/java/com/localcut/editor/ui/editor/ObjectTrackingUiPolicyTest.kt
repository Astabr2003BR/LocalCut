package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.GlobalMotionSample
import com.localcut.editor.domain.animation.GlobalMotionStabilizationPlanner
import com.localcut.editor.domain.animation.MotionOperationKind
import com.localcut.editor.domain.animation.MotionOperationPlanner
import com.localcut.editor.domain.animation.MotionOperationSpec
import com.localcut.editor.domain.animation.MotionPathPlanner
import com.localcut.editor.domain.animation.MotionPathPoint
import com.localcut.editor.domain.animation.ObjectTrackingPathPlanner
import com.localcut.editor.domain.animation.ObjectTrackingPositionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingUiPolicyTest {
    private fun tracking() = ObjectTrackingPathPlanner.plan(
        samples = listOf(ObjectTrackingPositionSample(100_000L, 0.1, 0.2, 0.9)),
        renderWidthPx = 1000.0,
        renderHeightPx = 500.0,
        baseX = 0.0,
        baseY = 0.0,
    )

    @Test
    fun mergePreservesUnrelatedAnimationAndRemovalIsScopedToGeneratedTracking() {
        val rotation = MotionOperationPlanner.toAnimation(
            MotionOperationSpec(
                id = "rotation",
                kind = MotionOperationKind.ROTATE,
                startOffsetUs = 0L,
                durationUs = 100_000L,
                from = 0.0,
                to = 15.0,
            ),
        )

        val merged = ObjectTrackingUiPolicy.merge(listOf(rotation), tracking())

        assertEquals(setOf("transform.rotationDeg", "transform.x", "transform.y"), merged.map { it.propertyId }.toSet())
        assertTrue(ObjectTrackingUiPolicy.hasGeneratedObjectTracking(merged))
        val removed = ObjectTrackingUiPolicy.removeGeneratedObjectTracking(merged)
        assertEquals(listOf("transform.rotationDeg"), removed.map { it.propertyId })
        assertFalse(ObjectTrackingUiPolicy.hasGeneratedObjectTracking(removed))
    }

    @Test
    fun mergeRejectsGeneratedStabilizationAndMotionPath() {
        val stabilized = GlobalMotionStabilizationPlanner.plan(
            samples = listOf(GlobalMotionSample(100_000L, 2.0, 1.0, 0.0, 1.0, 1.0)),
        )
        val stabilizeConflict = runCatching { ObjectTrackingUiPolicy.merge(stabilized, tracking()) }
        assertTrue(stabilizeConflict.isFailure)
        assertEquals("OBJECT_TRACKING_CONFLICTS_WITH_STABILIZATION", stabilizeConflict.exceptionOrNull()?.message)

        val path = MotionPathPlanner.plan(
            listOf(
                MotionPathPoint("a", 0L, 0.0, 0.0),
                MotionPathPoint("b", 100_000L, 10.0, 10.0),
            ),
        )
        val motionPathConflict = runCatching { ObjectTrackingUiPolicy.merge(path, tracking()) }
        assertTrue(motionPathConflict.isFailure)
        assertEquals("OBJECT_TRACKING_CONFLICTS_WITH_MOTION_PATH", motionPathConflict.exceptionOrNull()?.message)
    }
}
