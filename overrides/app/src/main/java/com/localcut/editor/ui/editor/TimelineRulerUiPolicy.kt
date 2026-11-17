package com.localcut.editor.ui.editor

import kotlin.math.ceil
import kotlin.math.max

object TimelineRulerUiPolicy {
    const val MIN_MAJOR_TICK_DP = 56f
    const val MAX_MAJOR_TICKS = 240L

    private val candidateIntervalsUs = longArrayOf(
        250_000L,
        500_000L,
        1_000_000L,
        2_000_000L,
        5_000_000L,
        10_000_000L,
        15_000_000L,
        30_000_000L,
        60_000_000L,
        120_000_000L,
        300_000_000L,
        600_000_000L,
        900_000_000L,
        1_800_000_000L,
        3_600_000_000L,
        7_200_000_000L,
        21_600_000_000L,
    )

    fun majorTickIntervalUs(
        durationUs: Long,
        dpPerSecond: Float,
    ): Long {
        require(durationUs >= 0L) { "durationUs must be non-negative" }
        require(dpPerSecond.isFinite() && dpPerSecond > 0f) {
            "dpPerSecond must be finite and positive"
        }

        val spacingBoundUs = ceil(MIN_MAJOR_TICK_DP / dpPerSecond * 1_000_000.0).toLong()
        val countBoundUs = if (durationUs == 0L) 0L else ceil(durationUs / MAX_MAJOR_TICKS.toDouble()).toLong()
        val requiredUs = max(spacingBoundUs, countBoundUs).coerceAtLeast(1L)

        candidateIntervalsUs.firstOrNull { it >= requiredUs }?.let { return it }

        val largest = candidateIntervalsUs.last()
        val multiples = ceil(requiredUs / largest.toDouble()).toLong().coerceAtLeast(1L)
        return Math.multiplyExact(largest, multiples)
    }

    fun majorTicksUs(
        durationUs: Long,
        dpPerSecond: Float,
    ): List<Long> {
        require(durationUs >= 0L) { "durationUs must be non-negative" }
        if (durationUs == 0L) return listOf(0L)

        val intervalUs = majorTickIntervalUs(durationUs, dpPerSecond)
        val ticks = ArrayList<Long>()
        var timeUs = 0L
        while (timeUs <= durationUs) {
            ticks += timeUs
            if (timeUs > Long.MAX_VALUE - intervalUs) break
            timeUs += intervalUs
        }
        return ticks
    }

    fun formatTimeUs(timeUs: Long): String {
        require(timeUs >= 0L) { "timeUs must be non-negative" }
        val totalSeconds = timeUs / 1_000_000L
        val seconds = totalSeconds % 60L
        val totalMinutes = totalSeconds / 60L
        val minutes = totalMinutes % 60L
        val hours = totalMinutes / 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(totalMinutes, seconds)
        }
    }
}
