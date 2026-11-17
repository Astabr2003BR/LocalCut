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
 * User-triggered visual shapes; not automatic speech recognition/alignment.
 * Never replaces existing TALK or edits source media.
 */
object VisemeMouthDemo {
    const val TYPE_ID = "localcut.character.mouth_warp"
    const val ID_PREFIX = "localcut-viseme-demo-"
    const val MIN_DURATION_US = 800_000L
    private const val MAX_DEMO_US = 4_000_000L

    fun apply(
        clipId: String,
        clipDurationUs: Long,
        existing: List<EffectInstance>,
        centerX: Double,
        centerY: Double,
        radiusX: Double,
        radiusY: Double,
    ): List<EffectInstance> {
        require(clipId.isNotBlank() && clipId.length <= 256) { "Clipe inválido" }
        require(clipDurationUs >= MIN_DURATION_US) { "Imagem muito curta para testar os visemas" }
        require(listOf(centerX, centerY, radiusX, radiusY).all(Double::isFinite)) {
            "Coordenadas da boca inválidas"
        }
        require(radiusX in 0.01..0.50 && radiusY in 0.005..0.30)
        require(centerX in radiusX..(1.0 - radiusX))
        require(centerY in radiusY..(1.0 - radiusY))
        require(existing.none { it.typeId == TYPE_ID }) {
            "Este clipe já tem TALK. Não substituímos o movimento existente."
        }
        val id = ID_PREFIX + clipId
        require(existing.none { it.id == id }) { "Efeito com ID duplicado" }

        val demoUs = minOf(clipDurationUs, MAX_DEMO_US)
        val startUs = demoUs / 10L
        val shapeSpanUs = (demoUs * 3L / 4L) / 8L
        val shapes = listOf(
            VisemeShape.A, VisemeShape.E_I, VisemeShape.O, VisemeShape.U,
            VisemeShape.F_V, VisemeShape.M_B_P, VisemeShape.L, VisemeShape.S_T,
        )
        val cues = shapes.mapIndexed { index, shape ->
            VisemeCue(startUs + index * shapeSpanUs, shapeSpanUs, shape)
        }
        val timeline = VisemeTimeline(cues, demoUs)
        val times = sortedSetOf(0L, demoUs)
        cues.forEach { cue ->
            val attack = minOf(40_000L, cue.durationUs / 3L)
            val release = minOf(50_000L, cue.durationUs / 3L)
            times.add(cue.offsetUs)
            times.add(cue.offsetUs + attack)
            times.add(cue.endUs - release)
            times.add(cue.endUs)
        }
        val poses = times.map { at -> at to timeline.poseAt(at) }
        val props = listOf<Pair<String, (VisemePose) -> Double>>(
            "openness" to { it.openness },
            "widthScale" to { it.widthScale },
            "rounding" to { it.rounding },
            "teethVisibility" to { it.teethVisibility },
        )
        val animations = props.map { (name, value) ->
            PropertyAnimation(
                propertyId = name,
                keyframes = poses.mapIndexed { index, (at, pose) ->
                    PropertyKeyframe(
                        id = "viseme-" + name + "-" + index,
                        offsetUs = at,
                        value = PropertyValue.Scalar(value(pose)),
                        easingToNext = Easing(EasingKind.EASE_IN_OUT),
                    )
                },
            )
        }
        val effect = EffectInstance(
            id = id, typeId = TYPE_ID, enabled = true,
            parameters = mapOf(
                "center" to EffectParameter.Point(centerX, centerY),
                "radiusX" to EffectParameter.Number(radiusX),
                "radiusY" to EffectParameter.Number(radiusY),
                "openness" to EffectParameter.Number(0.0),
                "widthScale" to EffectParameter.Number(1.0),
                "rounding" to EffectParameter.Number(0.0),
                "teethVisibility" to EffectParameter.Number(0.0),
            ),
            animations = animations,
        )
        require(EffectRegistry.validationIssues(effect).isEmpty()) {
            "Não foi possível criar visemas válidos"
        }
        return existing + effect
    }

    fun remove(existing: List<EffectInstance>): List<EffectInstance> =
        existing.filterNot { it.typeId == TYPE_ID && it.id.startsWith(ID_PREFIX) }
}
