package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.animation.TransformInterpolation
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransformKeyframe

object TimelineSlicer {
    fun slice(project: Project, startUs: Long, endUs: Long): Project {
        require(startUs >= 0) { "slice start must be >= 0" }
        require(endUs > startUs) { "slice end must be > start" }
        val projectDuration = TimelineMath.durationUs(project)
        require(endUs <= projectDuration) { "slice end exceeds project duration" }

        val tracks = project.tracks.map { track ->
            track.copy(clips = track.clips.mapNotNull { clip -> sliceClip(clip, startUs, endUs) })
        }
        val result = project.copy(tracks = tracks)
        val issues = TimelineValidator.validate(result)
        require(issues.isEmpty()) { "sliced project invalid: ${issues.firstOrNull()}" }
        return result
    }

    private fun sliceClip(clip: Clip, rangeStartUs: Long, rangeEndUs: Long): Clip? {
        val clipEnd = checkedAdd(clip.startUs, clip.durationUs)
        val intersectionStart = maxOf(clip.startUs, rangeStartUs)
        val intersectionEnd = minOf(clipEnd, rangeEndUs)
        if (intersectionStart >= intersectionEnd) return null

        val leftTrimUs = intersectionStart - clip.startUs
        val rightLocalUs = intersectionEnd - clip.startUs
        val newDurationUs = intersectionEnd - intersectionStart
        val newStartUs = intersectionStart - rangeStartUs
        val newSourceInUs = checkedAdd(clip.sourceInUs, leftTrimUs)
        val newSourceOutUs = checkedAdd(newSourceInUs, newDurationUs)

        val keyframes = sliceKeyframes(clip, leftTrimUs, rightLocalUs, newDurationUs)
        val fadeInUs = if (leftTrimUs == 0L) minOf(clip.fadeInUs, newDurationUs) else 0L
        val fadeOutUs = if (rightLocalUs == clip.durationUs) minOf(clip.fadeOutUs, newDurationUs - fadeInUs) else 0L
        return clip.copy(
            startUs = newStartUs,
            durationUs = newDurationUs,
            sourceInUs = newSourceInUs,
            sourceOutUs = newSourceOutUs,
            keyframes = keyframes,
            fadeInUs = fadeInUs,
            fadeOutUs = fadeOutUs,
        )
    }

    private fun sliceKeyframes(
        clip: Clip,
        leftTrimUs: Long,
        rightLocalUs: Long,
        newDurationUs: Long
    ): List<TransformKeyframe> {
        if (clip.keyframes.isEmpty()) return emptyList()
        if (leftTrimUs == 0L && rightLocalUs == clip.durationUs) return clip.keyframes

        val result = mutableListOf<TransformKeyframe>()
        result += TransformKeyframe(0L, TransformInterpolation.valueAt(clip.keyframes, leftTrimUs))
        clip.keyframes
            .asSequence()
            .filter { it.offsetUs > leftTrimUs && it.offsetUs < rightLocalUs }
            .sortedBy { it.offsetUs }
            .forEach { result += it.copy(offsetUs = it.offsetUs - leftTrimUs) }
        if (newDurationUs > 0L) {
            result += TransformKeyframe(
                newDurationUs,
                TransformInterpolation.valueAt(clip.keyframes, rightLocalUs)
            )
        }
        return result.distinctBy { it.offsetUs }.sortedBy { it.offsetUs }
    }

    private fun checkedAdd(a: Long, b: Long): Long = try {
        Math.addExact(a, b)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("timeline arithmetic overflow")
    }
}
