package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind

enum class MotionPreset {
    FADE_IN,
    SLIDE_UP,
    POP_IN,
    SPIN_IN,
}

object MotionPresetLibrary {
    fun build(
        preset: MotionPreset,
        startOffsetUs: Long,
        durationUs: Long,
    ): List<MotionOperationSpec> {
        require(startOffsetUs >= 0L) { "motion preset start must be non-negative" }
        require(durationUs in 1L..MotionOperationPlanner.MAX_DURATION_US) { "motion preset duration out of range" }
        val easing = Easing(EasingKind.EASE_OUT)
        fun operation(
            suffix: String,
            kind: MotionOperationKind,
            from: Double,
            to: Double,
        ) = MotionOperationSpec(
            id = "${preset.name.lowercase()}-$suffix",
            kind = kind,
            startOffsetUs = startOffsetUs,
            durationUs = durationUs,
            from = from,
            to = to,
            easing = easing,
        )

        val specs = when (preset) {
            MotionPreset.FADE_IN -> listOf(
                operation("opacity", MotionOperationKind.OPACITY, 0.0, 1.0),
            )
            MotionPreset.SLIDE_UP -> listOf(
                operation("move-y", MotionOperationKind.MOVE_Y, 80.0, 0.0),
                operation("opacity", MotionOperationKind.OPACITY, 0.0, 1.0),
            )
            MotionPreset.POP_IN -> listOf(
                operation("scale", MotionOperationKind.SCALE, 0.8, 1.0),
                operation("opacity", MotionOperationKind.OPACITY, 0.0, 1.0),
            )
            MotionPreset.SPIN_IN -> listOf(
                operation("rotate", MotionOperationKind.ROTATE, -15.0, 0.0),
                operation("scale", MotionOperationKind.SCALE, 0.9, 1.0),
                operation("opacity", MotionOperationKind.OPACITY, 0.0, 1.0),
            )
        }
        specs.forEach(MotionOperationPlanner::toAnimation)
        return specs
    }
}
