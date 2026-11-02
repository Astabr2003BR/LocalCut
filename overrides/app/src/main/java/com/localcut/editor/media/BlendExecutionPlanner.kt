package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode

sealed interface BlendBackdropRef {
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
        val passPlan = BlendPassPlanner.plan(modesByInputId)
        val targets = BlendRenderTargetPlanner.targetsFor(passPlan.passes.size)
        var backdrop: BlendBackdropRef = BlendBackdropRef.Input(passPlan.bottomInputId)
        val steps = passPlan.passes.mapIndexed { index, pass ->
            val target = targets[index]
            BlendExecutionStep(
                sourceInputId = pass.sourceInputId,
                mode = pass.mode,
                backdrop = backdrop,
                target = target,
            ).also {
                backdrop = BlendBackdropRef.Target(target)
            }
        }
        return BlendExecutionPlan(
            bottomInputId = passPlan.bottomInputId,
            steps = steps,
        )
    }
}
