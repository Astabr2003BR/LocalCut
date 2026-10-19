package com.localcut.editor.domain.timeline

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.*

enum class ProjectIssueCode {
    UNSUPPORTED_SCHEMA,
    INVALID_PROJECT_DIMENSIONS,
    INVALID_FPS,
    DUPLICATE_ASSET_ID,
    DUPLICATE_TRACK_ID,
    DUPLICATE_CLIP_ID,
    UNKNOWN_ASSET,
    NON_POSITIVE_DURATION,
    NEGATIVE_START,
    NEGATIVE_SOURCE_IN,
    INVALID_SOURCE_RANGE,
    SOURCE_RANGE_OUT_OF_BOUNDS,
    TIMELINE_OVERFLOW,
    TRACK_ASSET_TYPE_MISMATCH,
    INVALID_KEYFRAME,
    OVERLAPPING_CLIPS,
    DURATION_SOURCE_MISMATCH,
    INVALID_TRANSFORM,
    INVALID_OPACITY,
    INVALID_VOLUME
}

data class ProjectIssue(val code: ProjectIssueCode, val subjectId: String? = null, val detail: String? = null)

object TimelineValidator {
    fun validate(project: Project): List<ProjectIssue> {
        val issues = mutableListOf<ProjectIssue>()
        if (project.schemaVersion > BuildContract.PROJECT_SCHEMA_VERSION) {
            issues += ProjectIssue(ProjectIssueCode.UNSUPPORTED_SCHEMA, project.id)
        }
        if (project.width <= 0 || project.height <= 0) issues += ProjectIssue(ProjectIssueCode.INVALID_PROJECT_DIMENSIONS, project.id)
        if (project.fps <= 0) issues += ProjectIssue(ProjectIssueCode.INVALID_FPS, project.id)

        duplicateIds(project.assets.map { it.id }).forEach { issues += ProjectIssue(ProjectIssueCode.DUPLICATE_ASSET_ID, it) }
        duplicateIds(project.tracks.map { it.id }).forEach { issues += ProjectIssue(ProjectIssueCode.DUPLICATE_TRACK_ID, it) }
        val allClips = project.tracks.flatMap { it.clips }
        duplicateIds(allClips.map { it.id }).forEach { issues += ProjectIssue(ProjectIssueCode.DUPLICATE_CLIP_ID, it) }

        val assets = project.assets.associateBy { it.id }
        project.tracks.forEach { track ->
            if (track.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE) {
                val sorted = track.clips.sortedBy { it.startUs }
                sorted.zipWithNext().forEach { (a, b) ->
                    val aEnd = try { Math.addExact(a.startUs, a.durationUs) } catch (_: ArithmeticException) { Long.MAX_VALUE }
                    if (aEnd > b.startUs) issues += ProjectIssue(ProjectIssueCode.OVERLAPPING_CLIPS, b.id, "overlaps ${a.id}")
                }
            }
            track.clips.forEach { clip ->
                val asset = assets[clip.assetId]
                if (asset == null) {
                    issues += ProjectIssue(ProjectIssueCode.UNKNOWN_ASSET, clip.id)
                } else {
                    val mismatch = when (track.type) {
                        TrackType.VISUAL -> asset.type == AssetType.AUDIO
                        TrackType.AUDIO -> asset.type != AssetType.AUDIO
                    }
                    if (mismatch) issues += ProjectIssue(ProjectIssueCode.TRACK_ASSET_TYPE_MISMATCH, clip.id)
                    if (asset.sourceDurationUs != null && clip.sourceOutUs > asset.sourceDurationUs) {
                        issues += ProjectIssue(ProjectIssueCode.SOURCE_RANGE_OUT_OF_BOUNDS, clip.id)
                    }
                }
                if (clip.durationUs <= 0) issues += ProjectIssue(ProjectIssueCode.NON_POSITIVE_DURATION, clip.id)
                if (clip.startUs < 0) issues += ProjectIssue(ProjectIssueCode.NEGATIVE_START, clip.id)
                if (clip.sourceInUs < 0) issues += ProjectIssue(ProjectIssueCode.NEGATIVE_SOURCE_IN, clip.id)
                if (clip.sourceOutUs <= clip.sourceInUs) issues += ProjectIssue(ProjectIssueCode.INVALID_SOURCE_RANGE, clip.id)
                if (clip.sourceOutUs > clip.sourceInUs && clip.durationUs != clip.sourceOutUs - clip.sourceInUs) {
                    issues += ProjectIssue(ProjectIssueCode.DURATION_SOURCE_MISMATCH, clip.id)
                }
                if (!validTransform(clip.transform)) issues += ProjectIssue(ProjectIssueCode.INVALID_TRANSFORM, clip.id)
                if (!clip.opacity.isFinite() || clip.opacity !in 0.0..1.0) issues += ProjectIssue(ProjectIssueCode.INVALID_OPACITY, clip.id)
                if (!clip.volume.isFinite() || clip.volume < 0.0) issues += ProjectIssue(ProjectIssueCode.INVALID_VOLUME, clip.id)
                if (clip.durationUs > 0 && clip.startUs > Long.MAX_VALUE - clip.durationUs) {
                    issues += ProjectIssue(ProjectIssueCode.TIMELINE_OVERFLOW, clip.id)
                }
                val keyframeTimes = clip.keyframes.map { it.offsetUs }
                if (keyframeTimes.size != keyframeTimes.toSet().size || keyframeTimes.any { it < 0 || it > clip.durationUs }) {
                    issues += ProjectIssue(ProjectIssueCode.INVALID_KEYFRAME, clip.id)
                }
                if (clip.keyframes.any { !validTransform(it.value) }) {
                    issues += ProjectIssue(ProjectIssueCode.INVALID_TRANSFORM, clip.id, "invalid keyframe transform")
                }
            }
        }
        return issues
    }

    private fun validTransform(v: TransformValue): Boolean =
        v.x.isFinite() && v.y.isFinite() && v.scale.isFinite() && v.scale > 0.0 &&
            v.rotationDeg.isFinite() && v.opacity.isFinite() && v.opacity in 0.0..1.0

    private fun duplicateIds(ids: List<String>): Set<String> =
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
