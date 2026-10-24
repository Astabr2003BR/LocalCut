package com.localcut.editor.ui.editor

import kotlin.math.abs

private const val TIMELINE_US_PER_SECOND = 1_000_000.0
private const val MOBILE_SCRUB_SPAN_DP = 320.0
private const val LONG_TIMELINE_THRESHOLD_US = 20_000_000L

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

        val timelineDeltaUs = deltaDp.toDouble() / dpPerSecond.toDouble() * TIMELINE_US_PER_SECOND
        val deltaUs = if (safeDurationUs >= LONG_TIMELINE_THRESHOLD_US) {
            // A long timeline can be thousands of dp wide. On a phone the finger cannot travel
            // that distance in one gesture, so preserve direction but guarantee that a normal
            // screen-width scrub can traverse the whole project.
            val mobileDeltaUs = deltaDp.toDouble() / MOBILE_SCRUB_SPAN_DP * safeDurationUs.toDouble()
            if (abs(mobileDeltaUs) > abs(timelineDeltaUs)) mobileDeltaUs else timelineDeltaUs
        } else {
            timelineDeltaUs
        }
        return (safeStartUs.toDouble() + deltaUs)
            .coerceIn(0.0, safeDurationUs.toDouble())
            .toLong()
    }

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
