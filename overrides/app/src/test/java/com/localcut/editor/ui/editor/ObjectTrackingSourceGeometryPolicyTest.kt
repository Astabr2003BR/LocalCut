package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObjectTrackingSourceGeometryPolicyTest {
    @Test
    fun zeroAnd180KeepEncodedDimensions() {
        assertEquals(
            ObjectTrackingSourceGeometry(1920, 1080),
            ObjectTrackingSourceGeometryPolicy.oriented(1920, 1080, 0),
        )
        assertEquals(
            ObjectTrackingSourceGeometry(1920, 1080),
            ObjectTrackingSourceGeometryPolicy.oriented(1920, 1080, 180),
        )
    }

    @Test
    fun `90And270SwapDimensions`() {
        assertEquals(
            ObjectTrackingSourceGeometry(1080, 1920),
            ObjectTrackingSourceGeometryPolicy.oriented(1920, 1080, 90),
        )
        assertEquals(
            ObjectTrackingSourceGeometry(1080, 1920),
            ObjectTrackingSourceGeometryPolicy.oriented(1920, 1080, 270),
        )
    }

    @Test
    fun invalidDimensionsAndRotationFailClosed() {
        assertEquals(
            "OBJECT_TRACKING_SOURCE_SIZE_INVALID",
            assertThrows(IllegalArgumentException::class.java) {
                ObjectTrackingSourceGeometryPolicy.oriented(0, 1080, 0)
            }.message,
        )
        assertEquals(
            "OBJECT_TRACKING_SOURCE_ROTATION_INVALID",
            assertThrows(IllegalArgumentException::class.java) {
                ObjectTrackingSourceGeometryPolicy.oriented(1920, 1080, 45)
            }.message,
        )
    }
}
