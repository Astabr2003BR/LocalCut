package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.MouthRegion
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransformValue
import kotlin.math.min

/**
 * Maps source-image normalized bounds to the input framebuffer of the mouth shader.
 * ProjectCompositionCompiler adds Presentation.LAYOUT_SCALE_TO_FIT before the
 * per-clip effect stack, so raw source coordinates cannot be sent unchanged when
 * a portrait photo is placed on a landscape canvas (or vice versa).
 *
 * Only identity transforms/crop are supported for visual placement here. Nonidentity
 * camera, parent, keyframe or geometric effects require the full render inverse mapper.
 */
object MouthRegionRenderProjectionPolicy {
    fun supportsSourcePreview(project: Project, clip: Clip): Boolean {
        if (clip.transform != TransformValue() || clip.keyframes.isNotEmpty()) return false
        if (clip.propertyAnimations.any { it.propertyId.startsWith("transform.") }) return false
        if (clip.compositing.crop != CropSettings()) return false
        if (clip.effects.any { it.typeId != "localcut.character.mouth_warp" }) return false
        if (project.compositionGraph.cameraCuts.isNotEmpty()) return false
        val layer = project.compositionGraph.nodes.firstOrNull {
            it.kind == CompositionNodeKind.LAYER && it.clipId == clip.id
        }
        return layer == null || (layer.transform == LayerTransform3D() && layer.parentId == null)
    }

    fun fromSourceToEffect(
        source: MouthRegion,
        sourceWidth: Int,
        sourceHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ): MouthRegion {
        require(sourceWidth > 0 && sourceHeight > 0 && outputWidth > 0 && outputHeight > 0) {
            "invalid source or project dimensions"
        }
        require(source.centerX.isFinite() && source.centerX in 0.0..1.0 &&
            source.centerY.isFinite() && source.centerY in 0.0..1.0 &&
            source.radiusX.isFinite() && source.radiusX in 0.01..0.5 &&
            source.radiusY.isFinite() && source.radiusY in 0.005..0.30
        ) { "invalid source mouth region" }
        val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val outputAspect = outputWidth.toDouble() / outputHeight.toDouble()
        val fittedX = min(1.0, sourceAspect / outputAspect)
        val fittedY = min(1.0, outputAspect / sourceAspect)
        val radiusX = source.radiusX * fittedX
        val radiusY = source.radiusY * fittedY
        require(radiusX >= 0.01 && radiusY >= 0.005) {
            "Região muito pequena para o formato do projeto. Aumente o tamanho da boca."
        }
        return MouthRegion(
            centerX = 0.5 + (source.centerX - 0.5) * fittedX,
            centerY = 0.5 + (source.centerY - 0.5) * fittedY,
            radiusX = radiusX,
            radiusY = radiusY,
        )
    }
}
