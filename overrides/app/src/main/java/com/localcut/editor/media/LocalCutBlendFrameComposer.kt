package com.localcut.editor.media

import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.domain.model.BlendMode

@UnstableApi
internal data class BlendGpuInput(
    val inputId: Int,
    val texture: GlTextureInfo,
    val alphaScale: Float,
    val mode: BlendMode,
)

@UnstableApi
internal class LocalCutBlendFrameComposer(
    private val glProgram: LocalCutBlendGlProgram = LocalCutBlendGlProgram(),
) {
    fun compose(
        inputs: List<BlendGpuInput>,
        output: GlTextureInfo,
        scratchA: GlTextureInfo?,
        scratchB: GlTextureInfo?,
    ) {
        require(inputs.size >= 2) { "BLEND_COMPOSER_REQUIRES_MULTIPLE_INPUTS" }
        require(inputs.map { it.inputId } == inputs.indices.toList()) { "BLEND_INPUT_IDS_NOT_CONTIGUOUS" }
        inputs.forEach { input -> LocalCutBlendShaderContract.validateAlphaScale(input.alphaScale) }

        val plan = BlendExecutionPlanner.plan(inputs.map { it.mode })
        require(plan.steps.isNotEmpty()) { "BLEND_EXECUTION_PLAN_EMPTY" }

        val renderedTargets = mutableMapOf<BlendRenderTarget, GlTextureInfo>()
        plan.steps.forEach { step ->
            val (backdrop, backdropAlphaScale) = when (val ref = step.backdrop) {
                is BlendBackdropRef.Input -> inputs[ref.inputId].let { input -> input.texture to input.alphaScale }
                is BlendBackdropRef.Target -> {
                    val texture = renderedTargets[ref.target] ?: error("BLEND_BACKDROP_TARGET_UNAVAILABLE")
                    texture to 1f
                }
            }
            val source = inputs[step.sourceInputId]
            val target = when (step.target) {
                BlendRenderTarget.OUTPUT -> output
                BlendRenderTarget.SCRATCH_A -> scratchA ?: error("BLEND_SCRATCH_A_REQUIRED")
                BlendRenderTarget.SCRATCH_B -> scratchB ?: error("BLEND_SCRATCH_B_REQUIRED")
            }
            require(target.texId != backdrop.texId && target.texId != source.texture.texId) {
                "BLEND_TEXTURE_READ_WRITE_ALIAS"
            }

            glProgram.draw(
                backdrop = backdrop,
                source = source.texture,
                output = target,
                mode = step.mode,
                backdropAlphaScale = backdropAlphaScale,
                sourceAlphaScale = source.alphaScale,
            )
            renderedTargets[step.target] = target
        }
    }

    fun release() {
        glProgram.release()
    }
}
