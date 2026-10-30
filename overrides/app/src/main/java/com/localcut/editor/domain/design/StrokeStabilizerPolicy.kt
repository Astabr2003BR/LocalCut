package com.localcut.editor.domain.design

object StrokeStabilizerPolicy {
    const val MIN_STRENGTH = 0
    const val MAX_STRENGTH = 100

    fun smooth(
        points: List<CanvasPointPx>,
        strength: Int,
    ): List<CanvasPointPx> {
        require(strength in MIN_STRENGTH..MAX_STRENGTH) { "stabilization strength must be in 0..100" }
        require(points.all { it.x.isFinite() && it.y.isFinite() }) { "stroke points must be finite" }
        if (points.size < 3 || strength == 0) return points.toList()

        val blend = strength / MAX_STRENGTH.toDouble()
        val radius = when (strength) {
            in 1..25 -> 1
            in 26..50 -> 2
            in 51..75 -> 3
            else -> 4
        }

        return points.mapIndexed { index, point ->
            if (index == 0 || index == points.lastIndex) {
                point
            } else {
                val from = (index - radius).coerceAtLeast(0)
                val to = (index + radius).coerceAtMost(points.lastIndex)
                var sumX = 0.0
                var sumY = 0.0
                var count = 0
                for (sampleIndex in from..to) {
                    val sample = points[sampleIndex]
                    sumX += sample.x
                    sumY += sample.y
                    count += 1
                }
                val averageX = sumX / count
                val averageY = sumY / count
                CanvasPointPx(
                    x = point.x + (averageX - point.x) * blend,
                    y = point.y + (averageY - point.y) * blend,
                )
            }
        }
    }
}
