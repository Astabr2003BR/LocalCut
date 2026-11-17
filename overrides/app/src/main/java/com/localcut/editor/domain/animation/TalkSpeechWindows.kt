package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyValue

/** Bounded regions of existing TALK activity; no audio transcription or AI. */
internal data class TalkSpeechWindow(val startUs: Long, val endUs: Long) {
    init { require(startUs >= 0L && endUs > startUs) }
    val durationUs: Long get() = endUs - startUs
}

/** Converts positive TALK keyframe intervals into voice regions, retaining long pauses. */
internal object TalkSpeechWindows {
    private const val ACTIVE_THRESHOLD = 0.015
    private const val MERGE_GAP_US = 90_000L
    private const val MAX_WINDOWS = 32

    fun detect(openness: PropertyAnimation, clipDurationUs: Long): List<TalkSpeechWindow> {
        require(clipDurationUs in 1L..120_000_000L)
        require(openness.propertyId == "openness")
        require(openness.validationIssues().isEmpty())
        val frames = openness.keyframes
        val windows = ArrayList<TalkSpeechWindow>()
        frames.zipWithNext().forEach { (first, second) ->
            val from = (first.value as? PropertyValue.Scalar)?.value
                ?: throw IllegalArgumentException("TALK deve conter abertura escalar")
            val to = (second.value as? PropertyValue.Scalar)?.value
                ?: throw IllegalArgumentException("TALK deve conter abertura escalar")
            if (maxOf(from, to) <= ACTIVE_THRESHOLD) return@forEach
            val start = first.offsetUs.coerceIn(0L, clipDurationUs)
            val end = second.offsetUs.coerceIn(0L, clipDurationUs)
            if (end <= start) return@forEach
            val last = windows.lastOrNull()
            if (last != null && start - last.endUs <= MERGE_GAP_US) {
                windows[windows.lastIndex] = last.copy(endUs = end)
            } else {
                windows += TalkSpeechWindow(start, end)
            }
            require(windows.size <= MAX_WINDOWS) {
                "TALK possui pausas demais; divida a fala em clipes menores"
            }
        }
        require(windows.isNotEmpty()) { "TALK sem trechos audíveis de fala" }
        return windows
    }

    /**
     * Spreads transcript cues over concatenated speech regions, not wall-clock
     * time. A cue crossing a silent gap is split instead of holding its shape.
     * At most (cues + windows) intersections are emitted.
     */
    fun distribute(cues: List<VisemeCue>, windows: List<TalkSpeechWindow>): List<VisemeCue> {
        require(windows.isNotEmpty() && windows.size <= MAX_WINDOWS)
        require(windows.zipWithNext().all { (a, b) -> b.startUs >= a.endUs })
        val result = ArrayList<VisemeCue>(cues.size + windows.size)
        var cursorUs = 0L
        windows.forEach { window ->
            val segmentEndUs = cursorUs + window.durationUs
            cues.forEach { cue ->
                val overlapStart = maxOf(cursorUs, cue.offsetUs)
                val overlapEnd = minOf(segmentEndUs, cue.endUs)
                if (overlapEnd > overlapStart) {
                    result += VisemeCue(
                        offsetUs = window.startUs + overlapStart - cursorUs,
                        durationUs = overlapEnd - overlapStart,
                        shape = cue.shape,
                        strength = cue.strength,
                    )
                }
            }
            cursorUs = segmentEndUs
        }
        return result
    }
}
