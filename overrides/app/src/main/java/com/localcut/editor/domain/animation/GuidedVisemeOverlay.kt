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
    private val geometryProperties = setOf("widthScale", "rounding", "teethVisibility", "articulationGate")

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
        val windows = TalkSpeechWindows.detect(openness, clipDurationUs)
        val activeDurationUs = windows.sumOf(TalkSpeechWindow::durationUs)
        require(activeDurationUs >= PortugueseVisemeGuide.MIN_DURATION_US) {
            "Trecho falado muito curto para guiar visemas"
        }
        val abstractCues = PortugueseVisemeGuide.plan(transcript, activeDurationUs)
        val cues = TalkSpeechWindows.distribute(abstractCues, windows)
        val timeline = VisemeTimeline(cues, clipDurationUs)
        val positions = sortedSetOf(0L, clipDurationUs)
        windows.forEach { window ->
            positions.add(window.startUs)
            positions.add(window.endUs)
        }
        cues.forEach { cue ->
            val attack = minOf(40_000L, cue.durationUs / 3L)
            val release = minOf(50_000L, cue.durationUs / 3L)
            positions.add(cue.offsetUs)
            positions.add(cue.offsetUs + attack)
            positions.add(cue.endUs - release)
            positions.add(cue.endUs)
        }
        val frames = positions.map { position ->
            position to timeline.poseAt(position)
        }
        val properties = listOf<Pair<String, (VisemePose) -> Double>>(
            "widthScale" to { it.widthScale },
            "rounding" to { it.rounding },
            "teethVisibility" to { it.teethVisibility },
            // Keep the original waveform animation intact. A separate bounded
            // multiplier permits bilabial closure and brief text-guided rests.
            // This is still an approximate guide, not phoneme recognition.
            "articulationGate" to { (it.openness / 0.44).coerceIn(0.0, 1.0) },
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
