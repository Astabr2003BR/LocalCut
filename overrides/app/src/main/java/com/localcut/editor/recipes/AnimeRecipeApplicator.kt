package com.localcut.editor.recipes

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.effects.EffectRegistry
import java.util.Random

enum class AnimeRecipe {
    IMPACT_ZOOM,
    CAMERA_SHAKE,
    PARALLAX_PAN,
}

data class AnimeRecipeResult(
    val propertyAnimations: List<PropertyAnimation>,
    val effects: List<EffectInstance>,
) {
    fun validationIssues(): List<String> = buildList {
        propertyAnimations.forEach { addAll(it.validationIssues()) }
        effects.forEach { addAll(EffectRegistry.validationIssues(it)) }
    }
}

object AnimeRecipeApplicator {
    fun apply(
        clip: Clip,
        recipe: AnimeRecipe,
        seed: Long = 0L,
    ): AnimeRecipeResult {
        require(clip.durationUs > 0L) { "clip duration must be positive" }

        val generated = when (recipe) {
            AnimeRecipe.IMPACT_ZOOM -> listOf(impactZoom(clip.durationUs))
            AnimeRecipe.CAMERA_SHAKE -> cameraShake(clip.durationUs, seed)
            AnimeRecipe.PARALLAX_PAN -> parallaxPan(clip.durationUs)
        }
        val replacedProperties = generated.map { it.propertyId }.toSet()
        val mergedAnimations = clip.propertyAnimations.filterNot { it.propertyId in replacedProperties } + generated
        return AnimeRecipeResult(
            propertyAnimations = mergedAnimations,
            effects = clip.effects,
        ).also { result ->
            require(result.validationIssues().isEmpty()) {
                "invalid Anime Mode recipe result: ${result.validationIssues().first()}"
            }
        }
    }

    private fun impactZoom(durationUs: Long): PropertyAnimation {
        val impactUs = fractionalOffset(durationUs, 0.18)
        val settleUs = fractionalOffset(durationUs, 0.42).coerceAtLeast(impactUs + 1L).coerceAtMost(durationUs)
        val points = buildList {
            add(key("impact-scale-0", 0L, 1.0, EasingKind.EASE_OUT))
            if (impactUs in 1 until durationUs) add(key("impact-scale-1", impactUs, 1.16, EasingKind.EASE_OUT))
            if (settleUs in 1 until durationUs && settleUs != impactUs) add(key("impact-scale-2", settleUs, 1.03, EasingKind.EASE_IN_OUT))
            if (durationUs > 0L) add(key("impact-scale-3", durationUs, 1.0, EasingKind.EASE_IN_OUT))
        }.distinctBy { it.offsetUs }.sortedBy { it.offsetUs }
        return PropertyAnimation("transform.scale", points)
    }

    private fun cameraShake(durationUs: Long, seed: Long): List<PropertyAnimation> {
        val random = Random(seed)
        val count = 7
        val offsets = (0 until count).map { index ->
            if (index == count - 1) durationUs else (durationUs * index) / (count - 1)
        }.distinct()
        fun axis(propertyId: String, salt: Long): PropertyAnimation {
            val axisRandom = Random(seed xor salt)
            val frames = offsets.mapIndexed { index, offset ->
                val value = if (index == 0 || index == offsets.lastIndex) 0.0 else (axisRandom.nextDouble() * 2.0 - 1.0) * 18.0
                key("${propertyId.replace('.', '-')}-${index}", offset, value, EasingKind.LINEAR)
            }
            return PropertyAnimation(propertyId, frames)
        }
        random.nextLong() // keep deterministic seed consumption stable for future recipe extensions
        return listOf(
            axis("transform.x", 0x58414B45L),
            axis("transform.y", 0x59414B45L),
        )
    }

    private fun parallaxPan(durationUs: Long): List<PropertyAnimation> = listOf(
        PropertyAnimation(
            propertyId = "transform.x",
            keyframes = listOf(
                key("parallax-x-0", 0L, -24.0, EasingKind.EASE_IN_OUT),
                key("parallax-x-1", durationUs, 24.0, EasingKind.EASE_IN_OUT),
            ),
        ),
        PropertyAnimation(
            propertyId = "transform.y",
            keyframes = listOf(
                key("parallax-y-0", 0L, 10.0, EasingKind.EASE_IN_OUT),
                key("parallax-y-1", durationUs, -10.0, EasingKind.EASE_IN_OUT),
            ),
        ),
        PropertyAnimation(
            propertyId = "transform.scale",
            keyframes = listOf(
                key("parallax-scale-0", 0L, 1.03, EasingKind.EASE_IN_OUT),
                key("parallax-scale-1", durationUs, 1.08, EasingKind.EASE_IN_OUT),
            ),
        ),
    )

    private fun key(
        id: String,
        offsetUs: Long,
        value: Double,
        easing: EasingKind,
    ): PropertyKeyframe = PropertyKeyframe(
        id = id,
        offsetUs = offsetUs,
        value = PropertyValue.Scalar(value),
        easingToNext = Easing(kind = easing),
    )

    private fun fractionalOffset(durationUs: Long, fraction: Double): Long =
        (durationUs.toDouble() * fraction).toLong().coerceIn(0L, durationUs)
}
