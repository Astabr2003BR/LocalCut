package com.localcut.editor.domain.design

object DrawStrokeCapturePolicy {
    const val MAX_RAW_POINTS = 512
    private const val MIN_CAPTURE_DISTANCE_SQUARED = 1.0

    fun append(
        current: List<CanvasPointPx>,
        point: CanvasPointPx,
    ): List<CanvasPointPx> {
        require(point.x.isFinite() && point.y.isFinite()) { "capture point must be finite" }
        if (current.isEmpty()) return listOf(point)
        val previous = current.last()
        if (distanceSquared(previous, point) < MIN_CAPTURE_DISTANCE_SQUARED) return current
        if (current.size < MAX_RAW_POINTS) return current + point

        val decimated = buildList<CanvasPointPx> {
            add(current.first())
            var index = 2
            while (index < current.size) {
                add(current[index])
                index += 2
            }
        }
        return decimated + point
    }

    fun canCommit(points: List<CanvasPointPx>): Boolean =
        points.size >= 2 && distanceSquared(points.first(), points.last()) >= MIN_CAPTURE_DISTANCE_SQUARED

    private fun distanceSquared(left: CanvasPointPx, right: CanvasPointPx): Double {
        val dx = left.x - right.x
        val dy = left.y - right.y
        return dx * dx + dy * dy
    }
}
