package com.localcut.editor.ui.editor

import kotlin.math.abs
import kotlin.math.roundToLong

object TimelineSnapUiPolicy {
    data class ClipEdges(
        val clipId: String,
        val startUs: Long,
        val endUs: Long,
    ) {
        init {
            require(clipId.isNotBlank()) { "clipId must not be blank" }
            require(startUs >= 0L) { "clip start must be non-negative" }
            require(endUs >= startUs) { "clip end must not precede start" }
        }
    }

    fun toleranceUs(
        dpPerSecond: Float,
        toleranceDp: Float = 8f,
    ): Long {
        require(dpPerSecond > 0f && dpPerSecond.isFinite()) { "timeline zoom must be finite and positive" }
        require(toleranceDp >= 0f && toleranceDp.isFinite()) { "snap tolerance must be finite and non-negative" }
        return (toleranceDp.toDouble() / dpPerSecond.toDouble() * 1_000_000.0)
            .roundToLong()
            .coerceAtLeast(0L)
    }

    fun candidates(
        clipEdgesUs: List<ClipEdges>,
        excludedClipId: String?,
        playheadUs: Long,
        beatTimesUs: List<Long>,
    ): List<Long> = buildSet {
        clipEdgesUs
            .asSequence()
            .filterNot { it.clipId == excludedClipId }
            .forEach { edges ->
                add(edges.startUs)
                add(edges.endUs)
            }
        if (playheadUs >= 0L) add(playheadUs)
        beatTimesUs.filter { it >= 0L }.forEach(::add)
    }.sorted()

    fun snapEdge(
        requestedUs: Long,
        candidatesUs: List<Long>,
        toleranceUs: Long,
    ): Long {
        require(toleranceUs >= 0L) { "snap tolerance must be non-negative" }
        val target = requestedUs.coerceAtLeast(0L)
        val nearest = candidatesUs
            .asSequence()
            .filter { it >= 0L }
            .map { candidate -> candidate to absoluteDistance(candidate, target) }
            .minWithOrNull(compareBy<Pair<Long, Long>> { it.second }.thenBy { it.first })
            ?: return target
        return if (nearest.second <= toleranceUs) nearest.first else target
    }

    fun snapMove(
        requestedStartUs: Long,
        clipDurationUs: Long,
        candidatesUs: List<Long>,
        toleranceUs: Long,
    ): Long {
        require(clipDurationUs > 0L) { "clip duration must be positive" }
        require(toleranceUs >= 0L) { "snap tolerance must be non-negative" }

        val start = requestedStartUs.coerceAtLeast(0L)
        val end = if (start > Long.MAX_VALUE - clipDurationUs) Long.MAX_VALUE else start + clipDurationUs

        data class Match(val resultingStartUs: Long, val distanceUs: Long, val anchorUs: Long)

        val match = candidatesUs
            .asSequence()
            .filter { it >= 0L }
            .flatMap { anchor ->
                sequenceOf(
                    Match(anchor, absoluteDistance(anchor, start), anchor),
                    Match((anchor - clipDurationUs).coerceAtLeast(0L), absoluteDistance(anchor, end), anchor),
                )
            }
            .minWithOrNull(
                compareBy<Match> { it.distanceUs }
                    .thenBy { it.anchorUs }
                    .thenBy { it.resultingStartUs },
            )
            ?: return start

        return if (match.distanceUs <= toleranceUs) match.resultingStartUs else start
    }

    private fun absoluteDistance(a: Long, b: Long): Long = when {
        a >= b -> a - b
        else -> b - a
    }
}
