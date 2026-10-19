package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project

fun TimelineOps.duplicateClip(
    project: Project,
    clipId: String,
    duplicateClipId: String,
): Project {
    if (project.findClip(duplicateClipId) != null) {
        throw TimelineEditException(TimelineErrorCode.DUPLICATE_CLIP_ID, "duplicate clip id $duplicateClipId")
    }

    val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
    if (trackIndex < 0) {
        throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found")
    }
    val track = project.tracks[trackIndex]
    val clipIndex = track.clips.indexOfFirst { it.id == clipId }
    val original = track.clips[clipIndex]
    val duplicateStartUs = checkedClipEnd(original)

    val clips = track.clips.map { clip ->
        if (clip.id != original.id && clip.startUs >= duplicateStartUs) {
            clip.copy(startUs = checkedTimelineAdd(clip.startUs, original.durationUs))
        } else {
            clip
        }
    }.toMutableList().also {
        it.add(
            clipIndex + 1,
            original.copy(id = duplicateClipId, startUs = duplicateStartUs),
        )
    }

    val tracks = project.tracks.toMutableList().also {
        it[trackIndex] = track.copy(clips = clips)
    }
    val duplicated = project.copy(tracks = tracks)
    val issues = TimelineValidator.validate(duplicated)
    if (issues.isNotEmpty()) {
        val first = issues.first()
        val code = when (first.code) {
            ProjectIssueCode.DUPLICATE_CLIP_ID -> TimelineErrorCode.DUPLICATE_CLIP_ID
            ProjectIssueCode.SOURCE_RANGE_OUT_OF_BOUNDS -> TimelineErrorCode.SOURCE_RANGE_OUT_OF_BOUNDS
            else -> TimelineErrorCode.INVALID_PROJECT
        }
        throw TimelineEditException(code, "invalid project: $first")
    }
    return duplicated
}

private fun checkedClipEnd(clip: Clip): Long = try {
    Math.addExact(clip.startUs, clip.durationUs)
} catch (_: ArithmeticException) {
    throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "timeline overflow for clip ${clip.id}")
}

private fun checkedTimelineAdd(value: Long, delta: Long): Long = try {
    Math.addExact(value, delta)
} catch (_: ArithmeticException) {
    throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "timeline overflow")
}
