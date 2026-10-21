package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.TransformInterpolation
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue

object KeyframeEditorPolicy {
    fun localOffsetUs(clip: Clip, projectTimeUs: Long): Long? {
        val endUs = clipEndUs(clip) ?: return null
        if (projectTimeUs < clip.startUs || projectTimeUs > endUs) return null
        return projectTimeUs - clip.startUs
    }

    fun valueAtProjectTime(clip: Clip, projectTimeUs: Long): TransformValue? {
        val localUs = localOffsetUs(clip, projectTimeUs) ?: return null
        return if (clip.keyframes.isEmpty()) {
            clip.transform
        } else {
            TransformInterpolation.valueAt(clip.keyframes, localUs)
        }
    }

    fun upsertAtProjectTime(
        clip: Clip,
        projectTimeUs: Long,
        value: TransformValue,
    ): List<TransformKeyframe>? {
        val localUs = localOffsetUs(clip, projectTimeUs) ?: return null
        val replacement = TransformKeyframe(localUs, value)
        return clip.keyframes
            .filterNot { it.offsetUs == localUs }
            .plus(replacement)
            .sortedBy { it.offsetUs }
    }

    fun deleteAtProjectTime(clip: Clip, projectTimeUs: Long): List<TransformKeyframe>? {
        val localUs = localOffsetUs(clip, projectTimeUs) ?: return null
        return clip.keyframes.filterNot { it.offsetUs == localUs }
    }

    fun previousProjectTimeUs(clip: Clip, projectTimeUs: Long): Long? {
        val localUs = localOffsetUs(clip, projectTimeUs) ?: return null
        val previous = clip.keyframes
            .asSequence()
            .map { it.offsetUs }
            .filter { it < localUs }
            .maxOrNull()
            ?: return null
        return Math.addExact(clip.startUs, previous)
    }

    fun nextProjectTimeUs(clip: Clip, projectTimeUs: Long): Long? {
        val localUs = localOffsetUs(clip, projectTimeUs) ?: return null
        val next = clip.keyframes
            .asSequence()
            .map { it.offsetUs }
            .filter { it > localUs }
            .minOrNull()
            ?: return null
        return Math.addExact(clip.startUs, next)
    }

    fun markerFractions(clip: Clip): List<Float> {
        if (clip.durationUs <= 0L) return emptyList()
        return clip.keyframes
            .sortedBy { it.offsetUs }
            .map { keyframe ->
                (keyframe.offsetUs.toDouble() / clip.durationUs.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            }
    }

    private fun clipEndUs(clip: Clip): Long? = try {
        Math.addExact(clip.startUs, clip.durationUs)
    } catch (_: ArithmeticException) {
        null
    }
}
