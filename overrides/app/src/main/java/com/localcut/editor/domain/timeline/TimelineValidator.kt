package com.localcut.editor.domain.timeline

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.composition.LayerGraph
import com.localcut.editor.domain.model.*

enum class ProjectIssueCode {
    UNSUPPORTED_SCHEMA,
    INVALID_PROJECT_DIMENSIONS,
    INVALID_FPS,
    DUPLICATE_ASSET_ID,
    DUPLICATE_TRACK_ID,
    DUPLICATE_CLIP_ID,
    DUPLICATE_TEXT_OVERLAY_ID,
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
    INVALID_VOLUME,
    INVALID_AUDIO_FADE,
    INVALID_TRANSITION,
    INVALID_TEXT_OVERLAY,
    INVALID_CREATIVE_STATE,
    INVALID_COMPOSITION_GRAPH,
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
        duplicateIds(project.textOverlays.map { it.id }).forEach {
            issues += ProjectIssue(ProjectIssueCode.DUPLICATE_TEXT_OVERLAY_ID, it)
        }
        LayerGraph.validate(project.compositionGraph, allClips.map { it.id }.toSet()).firstOrNull()?.let { detail ->
            issues += ProjectIssue(ProjectIssueCode.INVALID_COMPOSITION_GRAPH, project.id, detail)
        }

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
                    if (asset.type == AssetType.IMAGE && (clip.fadeInUs != 0L || clip.fadeOutUs != 0L)) {
                        issues += ProjectIssue(ProjectIssueCode.INVALID_AUDIO_FADE, clip.id, "image clips cannot have audio fades")
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
                if (!validAudioFades(clip)) issues += ProjectIssue(ProjectIssueCode.INVALID_AUDIO_FADE, clip.id)
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
                creativeStateIssues(clip).firstOrNull()?.let { detail ->
                    issues += ProjectIssue(ProjectIssueCode.INVALID_CREATIVE_STATE, clip.id, detail)
                }
            }
        }

        validateTextOverlays(project, issues)
        validateTransitions(project, issues)
        return issues
    }

    private fun creativeStateIssues(clip: Clip): List<String> = buildList {
        if (clip.propertyAnimations.map { it.propertyId }.distinct().size != clip.propertyAnimations.size) {
            add("property animation propertyIds must be unique per clip")
        }
        clip.propertyAnimations.forEach { animation ->
            addAll(animation.validationIssues())
            if (animation.keyframes.any { it.offsetUs > clip.durationUs }) {
                add("property animation keyframe offset must be within clip duration")
            }
        }
        addAll(clip.compositing.validationIssues())
        if (clip.effects.map { it.id }.distinct().size != clip.effects.size) {
            add("effect ids must be unique per clip")
        }
        clip.effects.forEach { effect ->
            addAll(effect.validationIssues())
            if (effect.animations.any { animation -> animation.keyframes.any { it.offsetUs > clip.durationUs } }) {
                add("effect animation keyframe offset must be within clip duration")
            }
        }
        addAll(clip.timeMap.validationIssues())
        if (clip.timeMap.speedPoints.any { it.offsetUs > clip.durationUs }) {
            add("speed point offset must be within clip duration")
        }
        if (clip.timeMap.freezeSegments.any { freeze ->
                freeze.startOffsetUs > clip.durationUs ||
                    freeze.startOffsetUs > Long.MAX_VALUE - freeze.durationUs ||
                    freeze.startOffsetUs + freeze.durationUs > clip.durationUs
            }) {
            add("freeze segment must be within clip duration")
        }
    }

    private fun validateTextOverlays(project: Project, issues: MutableList<ProjectIssue>) {
        project.textOverlays.forEach { overlay ->
            val validTiming = overlay.startUs >= 0L && overlay.durationUs > 0L &&
                overlay.startUs <= Long.MAX_VALUE - overlay.durationUs
            val validText = overlay.text.isNotBlank()
            val validOpacity = overlay.opacity.isFinite() && overlay.opacity in 0.0..1.0
            val validStyle = overlay.style.fontSizePx.isFinite() && overlay.style.fontSizePx > 0.0 &&
                overlay.style.strokeWidthPx.isFinite() && overlay.style.strokeWidthPx >= 0.0 &&
                overlay.style.shadowRadiusPx.isFinite() && overlay.style.shadowRadiusPx >= 0.0 &&
                overlay.style.shadowOffsetX.isFinite() && overlay.style.shadowOffsetY.isFinite()
            if (!validTiming || !validText || !validOpacity || !validStyle || !validTransform(overlay.transform)) {
                issues += ProjectIssue(ProjectIssueCode.INVALID_TEXT_OVERLAY, overlay.id)
            }
        }
    }

    private fun validateTransitions(project: Project, issues: MutableList<ProjectIssue>) {
        data class ClipLocation(val track: Track, val clip: Clip, val index: Int)

        val locations = mutableMapOf<String, ClipLocation>()
        project.tracks.forEach { track ->
            track.clips.sortedBy { it.startUs }.forEachIndexed { index, clip ->
                locations[clip.id] = ClipLocation(track, clip, index)
            }
        }

        project.transitions.forEach { transition ->
            val from = locations[transition.fromClipId]
            val to = locations[transition.toClipId]
            val valid = if (from == null || to == null) {
                false
            } else {
                val sameTrack = from.track.id == to.track.id
                val sequentialVisualTrack = from.track.type == TrackType.VISUAL &&
                    from.track.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE
                val adjacent = sameTrack && to.index == from.index + 1
                val fromEnd = try {
                    Math.addExact(from.clip.startUs, from.clip.durationUs)
                } catch (_: ArithmeticException) {
                    Long.MIN_VALUE
                }
                val exactBoundary = adjacent && fromEnd == to.clip.startUs
                val validDuration = transition.durationUs > 0L &&
                    transition.durationUs <= minOf(from.clip.durationUs, to.clip.durationUs)
                sameTrack && sequentialVisualTrack && adjacent && exactBoundary && validDuration
            }
            if (!valid) {
                issues += ProjectIssue(ProjectIssueCode.INVALID_TRANSITION, transition.id)
            }
        }
    }

    private fun validAudioFades(clip: Clip): Boolean {
        if (clip.fadeInUs < 0L || clip.fadeOutUs < 0L || clip.durationUs <= 0L) return false
        if (clip.fadeInUs > clip.durationUs || clip.fadeOutUs > clip.durationUs) return false
        return clip.fadeInUs <= clip.durationUs - clip.fadeOutUs
    }

    private fun validTransform(v: TransformValue): Boolean =
        v.x.isFinite() && v.y.isFinite() && v.scale.isFinite() && v.scale > 0.0 &&
            v.rotationDeg.isFinite() && v.opacity.isFinite() && v.opacity in 0.0..1.0

    private fun duplicateIds(ids: List<String>): Set<String> =
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
