package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AssetType
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

    fun canAutoAnalyzeSelectedClip(project: Project, clipId: String?): Boolean {
        val clip = clipId?.let(project::findClip) ?: return false
        val asset = project.assets.firstOrNull { it.id == clip.assetId } ?: return false
        val timeMap = clip.timeMap
        return asset.type != AssetType.IMAGE &&
            clip.sourceOutUs > clip.sourceInUs &&
            !timeMap.reverse &&
            timeMap.freezeSegments.isEmpty() &&
            timeMap.speedPoints.isEmpty() &&
            kotlin.math.abs(timeMap.baseSpeed - 1.0) < 1e-9
    }

    fun autoMarkerPrefix(clipId: String): String {
        require(clipId.isNotBlank()) { "clipId must not be blank" }
        return "auto-beat-${clipId.replace(Regex("[^A-Za-z0-9_-]"), "_")}-"
    }

    fun hasAutoMarkersForClip(markers: List<BeatMarker>, clipId: String?): Boolean {
        if (clipId.isNullOrBlank()) return false
        val prefix = autoMarkerPrefix(clipId)
        return markers.any { it.id.startsWith(prefix) }
    }

    fun mergeAutoDetectedMarkers(
        existing: List<BeatMarker>,
        clipId: String,
        clipStartUs: Long,
        clipDurationUs: Long,
        detected: List<BeatMarker>,
    ): List<BeatMarker> {
        require(clipId.isNotBlank()) { "clipId must not be blank" }
        require(clipStartUs >= 0L) { "clip start must be non-negative" }
        require(clipDurationUs > 0L) { "clip duration must be positive" }
        val prefix = autoMarkerPrefix(clipId)
        val preserved = existing.filterNot { it.id.startsWith(prefix) }
        val mapped = detected
            .filter { it.timeUs in 0L..clipDurationUs }
            .mapIndexed { index, marker ->
                BeatMarker(
                    id = "$prefix${index + 1}",
                    timeUs = clipStartUs + marker.timeUs,
                    strength = marker.strength,
                )
            }
        val merged = (preserved + mapped).sortedWith(compareBy<BeatMarker> { it.timeUs }.thenBy { it.id })
        val issues = BeatMarker.validationIssues(merged)
        require(issues.isEmpty()) { "invalid mapped beat markers: ${issues.first()}" }
        return merged
    }

    fun markerFractions(project: Project): List<Float> {
        val durationUs = TimelineMath.durationUs(project)
        if (durationUs <= 0L) return emptyList()
        return project.beatMarkers.map { marker ->
            (marker.timeUs.toDouble() / durationUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
