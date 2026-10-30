package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode

sealed interface BlendBackdropRef {
    data object Transparent : BlendBackdropRef
    data class Input(val inputId: Int) : BlendBackdropRef
    data class Target(val target: BlendRenderTarget) : BlendBackdropRef
}

data class BlendExecutionStep(
    val sourceInputId: Int,
    val mode: BlendMode,
    val backdrop: BlendBackdropRef,
    val target: BlendRenderTarget,
)

data class BlendExecutionPlan(
    val bottomInputId: Int,
    val steps: List<BlendExecutionStep>,
)

object BlendExecutionPlanner {
    fun plan(modesByInputId: List<BlendMode>): BlendExecutionPlan {
        require(modesByInputId.isNotEmpty()) { "BLEND_INPUTS_EMPTY" }
        val bottomInputId = modesByInputId.lastIndex
        val sourceOrder = (bottomInputId downTo 0).toList()
        val targets = BlendRenderTargetPlanner.targetsFor(sourceOrder.size)
        var backdrop: BlendBackdropRef = BlendBackdropRef.Transparent
        val steps = sourceOrder.mapIndexed { index, inputId ->
            val target = targets[index]
            BlendExecutionStep(
                sourceInputId = inputId,
                mode = modesByInputId[inputId],
                backdrop = backdrop,
                target = target,
            ).also {
                backdrop = BlendBackdropRef.Target(target)
            }
        }
        return BlendExecutionPlan(
            bottomInputId = bottomInputId,
            steps = steps,
        )
    }
}
