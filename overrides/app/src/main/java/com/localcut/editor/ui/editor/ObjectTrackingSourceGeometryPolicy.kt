package com.localcut.editor.ui.editor

data class ObjectTrackingSourceGeometry(
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "OBJECT_TRACKING_SOURCE_SIZE_INVALID" }
    }
}

object ObjectTrackingSourceGeometryPolicy {
    fun oriented(
        encodedWidthPx: Int,
        encodedHeightPx: Int,
        rotationDeg: Int,
    ): ObjectTrackingSourceGeometry {
        require(encodedWidthPx > 0 && encodedHeightPx > 0) { "OBJECT_TRACKING_SOURCE_SIZE_INVALID" }
        require(rotationDeg in setOf(0, 90, 180, 270)) { "OBJECT_TRACKING_SOURCE_ROTATION_INVALID" }
        return if (rotationDeg == 90 || rotationDeg == 270) {
            ObjectTrackingSourceGeometry(encodedHeightPx, encodedWidthPx)
        } else {
            ObjectTrackingSourceGeometry(encodedWidthPx, encodedHeightPx)
        }
    }
}
