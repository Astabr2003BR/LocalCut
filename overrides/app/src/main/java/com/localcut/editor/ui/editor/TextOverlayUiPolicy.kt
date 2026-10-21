package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle

object TextOverlayUiPolicy {
    private const val DEFAULT_DURATION_US = 3_000_000L
    private const val MICROS_PER_SECOND = 1_000_000L

    fun createNew(
        project: Project,
        playheadUs: Long,
        text: String,
        style: TextStyle = TextStyle(),
    ): TextOverlay? {
        if (text.isBlank() || project.fps <= 0) return null

        val timelineDurationUs = project.tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .mapNotNull { clip ->
                if (clip.durationUs <= 0L || clip.startUs < 0L || clip.startUs > Long.MAX_VALUE - clip.durationUs) {
                    null
                } else {
                    clip.startUs + clip.durationUs
                }
            }
            .maxOrNull()
            ?: 0L
        if (timelineDurationUs <= 0L) return null

        val frameUs = (MICROS_PER_SECOND / project.fps.toLong()).coerceAtLeast(1L)
        val latestStartUs = (timelineDurationUs - minOf(frameUs, timelineDurationUs)).coerceAtLeast(0L)
        val startUs = playheadUs.coerceIn(0L, latestStartUs)
        val remainingUs = timelineDurationUs - startUs
        if (remainingUs <= 0L) return null

        return TextOverlay(
            id = nextId(project),
            startUs = startUs,
            durationUs = minOf(DEFAULT_DURATION_US, remainingUs),
            text = text,
            style = style,
            zIndex = nextZIndex(project),
        )
    }

    private fun nextId(project: Project): String {
        val used = project.textOverlays.asSequence().map { it.id }.toHashSet()
        var suffix = 1L
        while (true) {
            val candidate = "text-$suffix"
            if (candidate !in used) return candidate
            check(suffix < Long.MAX_VALUE) { "unable to allocate text overlay id" }
            suffix += 1L
        }
    }

    private fun nextZIndex(project: Project): Int {
        val currentMax = project.textOverlays.maxOfOrNull { it.zIndex } ?: -1
        return if (currentMax == Int.MAX_VALUE) Int.MAX_VALUE else currentMax + 1
    }
}
