package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath
import kotlin.math.abs

object BeatMarkerUiPolicy {
    const val NEAR_PLAYHEAD_TOLERANCE_US = 30_000L
    const val EDIT_SNAP_TOLERANCE_US = 80_000L

    fun nearestMarker(
        markers: List<BeatMarker>,
        playheadUs: Long,
        toleranceUs: Long = NEAR_PLAYHEAD_TOLERANCE_US,
    ): BeatMarker? {
        require(toleranceUs >= 0L) { "beat marker tolerance must be non-negative" }
        val target = playheadUs.coerceAtLeast(0L)
        return markers
            .minWithOrNull(compareBy<BeatMarker> { abs(it.timeUs - target) }.thenBy { it.timeUs }.thenBy { it.id })
            ?.takeIf { abs(it.timeUs - target) <= toleranceUs }
    }

    fun snapTimeUs(
        markers: List<BeatMarker>,
        requestedUs: Long,
        toleranceUs: Long = EDIT_SNAP_TOLERANCE_US,
    ): Long {
        require(toleranceUs >= 0L) { "beat snap tolerance must be non-negative" }
        val target = requestedUs.coerceAtLeast(0L)
        return nearestMarker(markers, target, toleranceUs)?.timeUs ?: target
    }

    fun manualMarker(project: Project, playheadUs: Long): BeatMarker? {
        val durationUs = TimelineMath.durationUs(project)
        if (durationUs <= 0L) return null
        val timeUs = playheadUs.coerceIn(0L, durationUs)
        if (nearestMarker(project.beatMarkers, timeUs) != null) return null

        val baseId = "manual-beat-$timeUs"
        val ids = project.beatMarkers.mapTo(hashSetOf()) { it.id }
        var candidate = baseId
        var suffix = 2
        while (candidate in ids) {
            candidate = "$baseId-$suffix"
            suffix += 1
        }
        return BeatMarker(
            id = candidate,
            timeUs = timeUs,
            strength = 1.0,
        )
    }

    fun markerFractions(project: Project): List<Float> {
        val durationUs = TimelineMath.durationUs(project)
        if (durationUs <= 0L) return emptyList()
        return project.beatMarkers.map { marker ->
            (marker.timeUs.toDouble() / durationUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
