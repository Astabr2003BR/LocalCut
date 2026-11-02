package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode

data class BlendPass(
    val sourceInputId: Int,
    val mode: BlendMode,
)

data class BlendPassPlan(
    val bottomInputId: Int,
    val passes: List<BlendPass>,
)

object BlendPassPlanner {
    fun plan(modesByInputId: List<BlendMode>): BlendPassPlan {
        require(modesByInputId.isNotEmpty()) { "BLEND_INPUTS_EMPTY" }
        val bottomInputId = modesByInputId.lastIndex
        val passes = (bottomInputId - 1 downTo 0).map { inputId ->
            BlendPass(
                sourceInputId = inputId,
                mode = modesByInputId[inputId],
            )
        }
        return BlendPassPlan(bottomInputId = bottomInputId, passes = passes)
    }
}
