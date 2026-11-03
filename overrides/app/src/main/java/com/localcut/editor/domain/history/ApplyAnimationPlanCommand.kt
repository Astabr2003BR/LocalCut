package com.localcut.editor.domain.history

import com.localcut.editor.domain.animation.CharacterAnimationPlan
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.timeline.TimelineEditException
import com.localcut.editor.domain.timeline.TimelineErrorCode
import com.localcut.editor.domain.timeline.TimelineValidator
import com.localcut.editor.domain.timeline.TrackEditGuard

data class ApplyAnimationPlanCommand(
    val plan: CharacterAnimationPlan,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val targets = locateTargets(project, plan.patches.map { it.clipId })
        targets.values.forEach { target -> TrackEditGuard.requireUnlocked(project.tracks[target.trackIndex]) }

        val previous = linkedMapOf<String, List<PropertyAnimation>>()
        val after = linkedMapOf<String, List<PropertyAnimation>>()
        val patchByClip = plan.patches.associateBy { it.clipId }

        val tracks = project.tracks.mapIndexed { trackIndex, track ->
            val affected = targets.filterValues { it.trackIndex == trackIndex }
            if (affected.isEmpty()) return@mapIndexed track
            val clips = track.clips.toMutableList()
            affected.forEach { (clipId, target) ->
                val clip = clips[target.clipIndex]
                val patch = requireNotNull(patchByClip[clipId])
                val ownedProperties = patch.propertyAnimations.map { it.propertyId }.toSet()
                require(ownedProperties.size == patch.propertyAnimations.size) {
                    "animation patch contains duplicate property ids: $clipId"
                }
                require(patch.propertyAnimations.all { animation ->
                    animation.validationIssues().isEmpty() &&
                        animation.keyframes.isNotEmpty() &&
                        animation.keyframes.all { keyframe -> keyframe.offsetUs in 0L..clip.durationUs }
                }) { "animation patch is invalid for clip $clipId" }

                previous[clipId] = clip.propertyAnimations
                val merged = mergePropertyAnimations(clip.propertyAnimations, patch.propertyAnimations)
                after[clipId] = merged
                clips[target.clipIndex] = clip.copy(propertyAnimations = merged)
            }
            track.copy(clips = clips)
        }

        val edited = project.copy(tracks = tracks)
        validate(edited, "animation plan")
        return CommandResult(
            project = edited,
            inverse = RestoreAnimationSnapshotsCommand(
                targetAnimations = previous,
                inverseAnimations = after,
            ),
        )
    }
}

private fun mergePropertyAnimations(
    existingAnimations: List<PropertyAnimation>,
    patchAnimations: List<PropertyAnimation>,
): List<PropertyAnimation> {
    require(existingAnimations.map { it.propertyId }.distinct().size == existingAnimations.size) {
        "existing property animation ids must be unique"
    }
    val remaining = patchAnimations.associateBy { it.propertyId }.toMutableMap()
    val merged = existingAnimations.map { existing ->
        val patch = remaining.remove(existing.propertyId) ?: return@map existing
        mergeAnimationWindow(existing, patch)
    }.toMutableList()
    merged += remaining.values
    require(merged.all { it.validationIssues().isEmpty() }) { "merged property animation is invalid" }
    return merged
}

private fun mergeAnimationWindow(existing: PropertyAnimation, patch: PropertyAnimation): PropertyAnimation {
    require(existing.propertyId == patch.propertyId) { "animation properties must match" }
    require(patch.keyframes.isNotEmpty()) { "patch animation must contain keyframes" }
    val startUs = patch.keyframes.first().offsetUs
    val endUs = patch.keyframes.last().offsetUs
    require(endUs >= startUs) { "patch animation window is invalid" }

    val combined = buildList {
        addAll(existing.keyframes.filter { it.offsetUs < startUs || it.offsetUs > endUs })
        addAll(patch.keyframes)
    }.sortedBy { it.offsetUs }
    require(combined.map { it.offsetUs }.distinct().size == combined.size) {
        "merged animation contains duplicate keyframe timestamps"
    }
    require(combined.map { it.id }.distinct().size == combined.size) {
        "merged animation contains duplicate keyframe ids"
    }
    return PropertyAnimation(
        propertyId = existing.propertyId,
        keyframes = combined,
    ).also { animation ->
        require(animation.validationIssues().isEmpty()) { "merged animation is invalid" }
    }
}

private data class RestoreAnimationSnapshotsCommand(
    val targetAnimations: Map<String, List<PropertyAnimation>>,
    val inverseAnimations: Map<String, List<PropertyAnimation>>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val targets = locateTargets(project, targetAnimations.keys)
        val tracks = project.tracks.mapIndexed { trackIndex, track ->
            val affected = targets.filterValues { it.trackIndex == trackIndex }
            if (affected.isEmpty()) return@mapIndexed track
            val clips = track.clips.toMutableList()
            affected.forEach { (clipId, target) ->
                clips[target.clipIndex] = clips[target.clipIndex].copy(
                    propertyAnimations = requireNotNull(targetAnimations[clipId]),
                )
            }
            track.copy(clips = clips)
        }
        val restored = project.copy(tracks = tracks)
        validate(restored, "animation history restore")
        return CommandResult(
            project = restored,
            inverse = RestoreAnimationSnapshotsCommand(
                targetAnimations = inverseAnimations,
                inverseAnimations = targetAnimations,
            ),
        )
    }
}

private data class AnimationClipLocation(
    val trackIndex: Int,
    val clipIndex: Int,
)

private fun locateTargets(project: Project, clipIds: Collection<String>): Map<String, AnimationClipLocation> {
    require(clipIds.isNotEmpty()) { "animation plan must target at least one clip" }
    val requested = clipIds.toSet()
    require(requested.size == clipIds.size) { "animation plan target clip ids must be unique" }
    val found = linkedMapOf<String, AnimationClipLocation>()
    project.tracks.forEachIndexed { trackIndex, track ->
        track.clips.forEachIndexed { clipIndex, clip ->
            if (clip.id !in requested) return@forEachIndexed
            if (found.put(clip.id, AnimationClipLocation(trackIndex, clipIndex)) != null) {
                throw TimelineEditException(
                    TimelineErrorCode.INVALID_PROJECT,
                    "clip ${clip.id} exists in more than one track",
                )
            }
        }
    }
    requested.forEach { clipId ->
        if (clipId !in found) {
            throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found")
        }
    }
    return found
}

private fun validate(project: Project, label: String) {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "$label produced invalid project: ${issues.first()}" }
}
