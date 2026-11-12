package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue

/** Explicit multi-layer actions for already separated, aligned image assets.
 * No segmentation, IK, automatic parenting, or project mutations. */
object LayeredCharacterActionPolicy {
    fun candidates(project: Project, bodyId: String): List<Clip> {
        val body = project.findClip(bodyId) ?: return emptyList()
        val imageIds = project.assets.filter { it.type == AssetType.IMAGE }.map { it.id }.toSet()
        val lockedNodes = project.compositionGraph.nodes.filter {
            it.kind == CompositionNodeKind.LAYER && it.locked
        }.mapNotNull { it.clipId }.toSet()
        return project.tracks.filter { it.type == TrackType.VISUAL && !it.locked && !it.hidden }
            .flatMap { it.clips }.filter {
                it.assetId in imageIds && it.id !in lockedNodes && it.startUs == body.startUs
            }
    }

    fun plan(project: Project, rig: CharacterRigBinding, action: CharacterAction,
             durationUs: Long, intensity: Double): CharacterAnimationPlan {
        require(action in setOf(CharacterAction.IDLE, CharacterAction.WALK, CharacterAction.POINT, CharacterAction.TALK))
        require(durationUs in 200_000L..30_000_000L && intensity.isFinite() && intensity in 0.0..1.0)
        val ids = listOfNotNull(rig.bodyClipId, rig.headClipId, rig.eyesClipId, rig.mouthClipId,
            rig.leftArmClipId, rig.rightArmClipId, rig.leftLegClipId, rig.rightLegClipId, rig.heldObjectClipId)
        require(ids.size >= 2 && ids.distinct().size == ids.size) { "Selecione partes distintas do personagem" }
        require(rig.heldObjectClipId == null) { "Objeto não suportado neste painel" }
        val valid = candidates(project, rig.bodyClipId).associateBy { it.id }
        require(ids.all { it in valid }) { "Camadas precisam estar visíveis, desbloqueadas e alinhadas no início" }
        require(ids.all { valid.getValue(it).durationUs >= durationUs }) { "A duração excede uma das camadas" }
        when (action) {
            CharacterAction.WALK -> require(listOf(rig.leftArmClipId, rig.rightArmClipId,
                rig.leftLegClipId, rig.rightLegClipId).all { it != null }) { "Marque os dois braços e as duas pernas" }
            CharacterAction.POINT -> require(rig.rightArmClipId != null) { "Marque o braço direito" }
            CharacterAction.TALK -> require(rig.mouthClipId != null) { "Marque a camada da boca" }
            else -> require(rig.headClipId != null || rig.eyesClipId != null) { "Marque cabeça ou olhos" }
        }
        require(ids.all { valid.getValue(it).keyframes.isEmpty() }) {
            "Uma camada possui keyframes de transformação legados; use uma cópia sem esses movimentos."
        }
        val states = ids.associateWith { id ->
            val clip = valid.getValue(id)
            fun scalar(property: String, fallback: Double): Double {
                val animation = clip.propertyAnimations.singleOrNull { it.propertyId == property } ?: return fallback
                if (animation.keyframes.isEmpty() || animation.keyframes.first().offsetUs > 0L) return fallback
                return (PropertyAnimationEvaluator.valueAt(animation, 0L) as? PropertyValue.Scalar)?.value
                    ?: throw IllegalArgumentException("Transformação incompatível")
            }
            AnimationClipState(id, clip.durationUs, TransformValue(
                x = scalar("transform.x", clip.transform.x),
                y = scalar("transform.y", clip.transform.y),
                scale = scalar("transform.scale", clip.transform.scale),
                rotationDeg = scalar("transform.rotationDeg", clip.transform.rotationDeg),
                opacity = scalar("transform.opacity", clip.transform.opacity),
            ))
        }
        // Walk in place avoids separating unparented parts via global translation.
        val plan = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(action, rig, 0L, durationUs, intensity = intensity, distancePx = 0.0),
            states,
        )
        // A multi-layer action must never silently replace a custom motion track.
        plan.patches.forEach { patch ->
            val clip = valid.getValue(patch.clipId)
            val properties = patch.propertyAnimations.map { it.propertyId }.toSet()
            require(clip.propertyAnimations.none { it.propertyId in properties }) {
                "Uma camada já possui movimento nessas propriedades. Use cópias ou remova os movimentos anteriores."
            }
        }
        // The legacy walking planner starts/ends at a swing extreme. For the
        // new in-place UI preset, enter and leave in the original pose instead.
        if (action != CharacterAction.WALK) return plan
        val limbs = setOfNotNull(rig.leftArmClipId, rig.rightArmClipId,
            rig.leftLegClipId, rig.rightLegClipId)
        return plan.copy(patches = plan.patches.map { patch ->
            if (patch.clipId !in limbs) patch else patch.copy(
                propertyAnimations = patch.propertyAnimations.map { animation ->
                    if (animation.propertyId != "transform.rotationDeg") animation else {
                        val rest = PropertyValue.Scalar(states.getValue(patch.clipId).transform.rotationDeg)
                        animation.copy(keyframes = animation.keyframes.mapIndexed { index, frame ->
                            if (index == 0 || index == animation.keyframes.lastIndex)
                                frame.copy(value = rest) else frame
                        })
                    }
                },
            )
        })
    }
}
