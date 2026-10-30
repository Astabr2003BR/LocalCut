package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.PropertyAnimation

enum class AnimationStaggerOrder {
    FORWARD,
    REVERSE,
}

data class AnimationStaggerTarget(
    val targetId: String,
    val animations: List<PropertyAnimation>,
)

data class PlannedAnimationStagger(
    val targetId: String,
    val delayUs: Long,
    val animations: List<PropertyAnimation>,
)

object StaggeredAnimationPlanner {
    const val MAX_TARGETS = 256
    const val MAX_STEP_US = 60_000_000L

    fun plan(
        targets: List<AnimationStaggerTarget>,
        stepUs: Long,
        order: AnimationStaggerOrder = AnimationStaggerOrder.FORWARD,
    ): List<PlannedAnimationStagger> {
        require(stepUs in 0L..MAX_STEP_US) { "stagger step must be in 0..$MAX_STEP_US us" }
        require(targets.size <= MAX_TARGETS) { "too many stagger targets" }
        require(targets.all { it.targetId.isNotBlank() }) { "stagger target id must not be blank" }
        require(targets.map { it.targetId }.distinct().size == targets.size) { "stagger target ids must be unique" }
        require(targets.all { it.animations.isNotEmpty() }) { "stagger target requires at least one animation" }
        targets.forEach { target ->
            require(target.animations.all { it.validationIssues().isEmpty() }) { "stagger target contains invalid animation" }
        }

        val lastIndex = targets.lastIndex
        return targets.mapIndexed { index, target ->
            val delayIndex = when (order) {
                AnimationStaggerOrder.FORWARD -> index
                AnimationStaggerOrder.REVERSE -> lastIndex - index
            }
            val delayUs = Math.multiplyExact(delayIndex.toLong(), stepUs)
            val shifted = target.animations.map { animation ->
                animation.copy(
                    keyframes = animation.keyframes.map { keyframe ->
                        keyframe.copy(offsetUs = Math.addExact(keyframe.offsetUs, delayUs))
                    },
                ).also { shiftedAnimation ->
                    require(shiftedAnimation.validationIssues().isEmpty()) { "stagger produced invalid animation" }
                }
            }
            PlannedAnimationStagger(
                targetId = target.targetId,
                delayUs = delayUs,
                animations = shifted,
            )
        }
    }
}
