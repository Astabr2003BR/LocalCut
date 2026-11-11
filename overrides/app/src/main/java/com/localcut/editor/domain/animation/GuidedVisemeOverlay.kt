package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.effects.EffectRegistry

/**
 * Adds ONLY guided mouth geometry animations to an existing audio-driven TALK.
 * Never re-times audio, changes the waveform openness, or touches blink effects.
 * Re-application is deterministic and one editor Undo via the caller.
 */
object GuidedVisemeOverlay {
    private const val MOUTH_TYPE = "localcut.character.mouth_warp"
    private const val DEMO_PREFIX = "localcut-viseme-demo-"
    private const val OWNED_PREFIX = "guided-viseme-"
    private val geometryProperties = setOf("widthScale", "rounding", "teethVisibility")

    fun hasGuidance(effect: EffectInstance): Boolean =
        effect.typeId == MOUTH_TYPE &&
            effect.animations.any { animation -> isOwned(animation) }

    private fun isOwned(animation: PropertyAnimation): Boolean =
        animation.propertyId in geometryProperties &&
            animation.keyframes.isNotEmpty() &&
            animation.keyframes.all { it.id.startsWith(OWNED_PREFIX) }

    fun apply(
        clipDurationUs: Long,
        existing: List<EffectInstance>,
        transcript: String,
    ): List<EffectInstance> {
        require(clipDurationUs > 0L && clipDurationUs <= 120_000_000L) {
            "Duração do clipe inválida"
        }
        val mouths = existing.filter { it.typeId == MOUTH_TYPE }
        require(mouths.size == 1 && !mouths.single().id.startsWith(DEMO_PREFIX)) {
            "Adicione o TALK com áudio antes de usar o texto"
        }
        val original = mouths.single()
        require(original.enabled && EffectRegistry.validationIssues(original).isEmpty()) {
            "O TALK existente está inválido ou desativado"
        }
        require(original.animations.none { animation ->
            animation.propertyId in geometryProperties && !isOwned(animation)
        }) { "O TALK contém animações de boca personalizadas; não serão sobrescritas" }
        val openness = original.animations.singleOrNull { it.propertyId == "openness" }
            ?: throw IllegalArgumentException("TALK não possui sincronização com o áudio")
        val active = openness.keyframes.filter { frame ->
            ((frame.value as? PropertyValue.Scalar)?.value ?: 0.0) > 0.01
        }
        require(active.isNotEmpty()) { "TALK não contém movimento de fala" }
        val begin = (active.first().offsetUs - 40_000L).coerceAtLeast(0L)
        val finish = (active.last().offsetUs + 65_000L).coerceAtMost(clipDurationUs)
        require(finish > begin && finish - begin >= PortugueseVisemeGuide.MIN_DURATION_US) {
            "Trecho falado muito curto para guiar visemas"
        }
        val span = finish - begin
        val cues = PortugueseVisemeGuide.plan(transcript, span)
        val timeline = VisemeTimeline(cues, span)
        val positions = sortedSetOf(0L, clipDurationUs, begin, finish)
        cues.forEach { cue ->
            val attack = minOf(40_000L, cue.durationUs / 3L)
            val release = minOf(50_000L, cue.durationUs / 3L)
            positions.add(begin + cue.offsetUs)
            positions.add(begin + cue.offsetUs + attack)
            positions.add(begin + cue.endUs - release)
            positions.add(begin + cue.endUs)
        }
        val frames = positions.map { position ->
            val pose = if (position in begin..finish) {
                timeline.poseAt(position - begin)
            } else VisemePose.REST
            position to pose
        }
        val properties = listOf<Pair<String, (VisemePose) -> Double>>(
            "widthScale" to { it.widthScale },
            "rounding" to { it.rounding },
            "teethVisibility" to { it.teethVisibility },
        )
        val newAnimations = properties.map { (name, select) ->
            PropertyAnimation(
                propertyId = name,
                keyframes = frames.mapIndexed { index, (at, pose) ->
                    PropertyKeyframe(
                        id = OWNED_PREFIX + name + "-" + index + "-" + at,
                        offsetUs = at,
                        value = PropertyValue.Scalar(select(pose)),
                        easingToNext = Easing(EasingKind.LINEAR),
                    )
                },
            )
        }
        val patched = original.copy(
            animations = original.animations.filterNot(::isOwned) + newAnimations,
        )
        require(EffectRegistry.validationIssues(patched).isEmpty()) {
            "Articulação guiada inválida; nenhuma alteração aplicada"
        }
        return existing.map { if (it.id == original.id && it.typeId == MOUTH_TYPE) patched else it }
    }

    fun remove(existing: List<EffectInstance>): List<EffectInstance> =
        existing.map { effect ->
            if (!hasGuidance(effect)) effect else effect.copy(
                animations = effect.animations.filterNot(::isOwned),
            )
        }
}
