package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType

enum class PreviewQualityTier(val maxLongEdge: Int) {
    LOW(360),
    BALANCED(540),
    HIGH(720),
}

enum class ThermalPressure {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
}

data class PreviewRuntimeConditions(
    val memoryClassMb: Int,
    val lowRamDevice: Boolean,
    val powerSaveMode: Boolean,
    val thermalPressure: ThermalPressure = ThermalPressure.NONE,
)

object AdaptivePreviewPolicy {
    fun choose(project: Project, runtime: PreviewRuntimeConditions): PreviewQualityTier {
        if (
            runtime.lowRamDevice ||
            runtime.powerSaveMode ||
            runtime.memoryClassMb < LOW_MEMORY_CLASS_MB ||
            runtime.thermalPressure >= ThermalPressure.SEVERE
        ) {
            return PreviewQualityTier.LOW
        }

        val visualClipCount = project.tracks
            .asSequence()
            .filter { it.type == TrackType.VISUAL }
            .sumOf { it.clips.size }
        val visualTrackCount = project.tracks.count { it.type == TrackType.VISUAL }

        if (
            runtime.memoryClassMb < BALANCED_MEMORY_CLASS_MB ||
            visualClipCount >= COMPLEX_CLIP_COUNT ||
            visualTrackCount >= COMPLEX_VISUAL_TRACK_COUNT
        ) {
            return PreviewQualityTier.BALANCED
        }

        return PreviewQualityTier.HIGH
    }

    private const val LOW_MEMORY_CLASS_MB = 192
    private const val BALANCED_MEMORY_CLASS_MB = 384
    private const val COMPLEX_CLIP_COUNT = 40
    private const val COMPLEX_VISUAL_TRACK_COUNT = 3
}
