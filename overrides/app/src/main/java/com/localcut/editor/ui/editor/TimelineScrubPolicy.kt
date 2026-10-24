package com.localcut.editor.ui.editor

private const val TIMELINE_US_PER_SECOND = 1_000_000.0

object TimelineScrubPolicy {
    fun positionForDrag(
        startUs: Long,
        deltaDp: Float,
        dpPerSecond: Float,
        durationUs: Long,
    ): Long {
        require(dpPerSecond.isFinite() && dpPerSecond > 0f) {
            "dpPerSecond must be finite and positive"
        }
        val safeDurationUs = durationUs.coerceAtLeast(0L)
        val safeStartUs = startUs.coerceIn(0L, safeDurationUs)
        if (!deltaDp.isFinite()) return safeStartUs

        val deltaUs = deltaDp.toDouble() / dpPerSecond.toDouble() * TIMELINE_US_PER_SECOND
        return (safeStartUs.toDouble() + deltaUs)
            .coerceIn(0.0, safeDurationUs.toDouble())
            .toLong()
    }

    /**
     * Maps a bounded on-screen scrub gesture to the whole project duration.
     * This is intended for mobile playheads where a long timeline may be much wider than
     * the physical viewport. A full-width drag can therefore always reach 0 or duration.
     */
    fun positionForViewportDrag(
        startUs: Long,
        deltaDp: Float,
        viewportWidthDp: Float,
        durationUs: Long,
    ): Long {
        require(viewportWidthDp.isFinite() && viewportWidthDp > 0f) {
            "viewportWidthDp must be finite and positive"
        }
        val safeDurationUs = durationUs.coerceAtLeast(0L)
        val safeStartUs = startUs.coerceIn(0L, safeDurationUs)
        if (!deltaDp.isFinite() || safeDurationUs == 0L) return safeStartUs

        val deltaUs = deltaDp.toDouble() / viewportWidthDp.toDouble() * safeDurationUs.toDouble()
        return (safeStartUs.toDouble() + deltaUs)
            .coerceIn(0.0, safeDurationUs.toDouble())
            .toLong()
    }

    fun positionForTap(
        xDp: Float,
        dpPerSecond: Float,
        durationUs: Long,
    ): Long {
        require(dpPerSecond.isFinite() && dpPerSecond > 0f) {
            "dpPerSecond must be finite and positive"
        }
        val safeDurationUs = durationUs.coerceAtLeast(0L)
        if (!xDp.isFinite()) return 0L

        return (xDp.toDouble() / dpPerSecond.toDouble() * TIMELINE_US_PER_SECOND)
            .coerceIn(0.0, safeDurationUs.toDouble())
            .toLong()
    }
}
