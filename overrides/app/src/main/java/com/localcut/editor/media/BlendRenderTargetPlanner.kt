package com.localcut.editor.media

enum class BlendRenderTarget {
    SCRATCH_A,
    SCRATCH_B,
    OUTPUT,
}

object BlendRenderTargetPlanner {
    fun targetsFor(passCount: Int): List<BlendRenderTarget> {
        require(passCount >= 0) { "BLEND_PASS_COUNT_INVALID" }
        if (passCount == 0) return emptyList()
        if (passCount == 1) return listOf(BlendRenderTarget.OUTPUT)

        return List(passCount) { index ->
            when {
                index == passCount - 1 -> BlendRenderTarget.OUTPUT
                index % 2 == 0 -> BlendRenderTarget.SCRATCH_A
                else -> BlendRenderTarget.SCRATCH_B
            }
        }
    }
}
