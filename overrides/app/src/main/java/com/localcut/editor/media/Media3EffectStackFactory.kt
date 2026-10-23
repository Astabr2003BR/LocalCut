package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import kotlin.math.pow

@OptIn(UnstableApi::class)
internal object Media3EffectStackFactory {
    private val supportedTypeIds = setOf(
        "localcut.color.brightness",
        "localcut.color.contrast",
        "localcut.color.exposure",
        "localcut.color.saturation",
        "localcut.color.temperature",
        "localcut.color.tint",
        "localcut.blur.gaussian",
    )

    fun canRender(effect: EffectInstance): Boolean =
        !effect.enabled ||
            (effect.typeId in supportedTypeIds &&
                effect.animations.isEmpty() &&
                EffectRegistry.validationIssues(effect).isEmpty())

    fun create(effects: List<EffectInstance>): List<Effect> = buildList {
        effects.forEach { effect ->
            if (!effect.enabled) return@forEach
            require(canRender(effect)) { "unsupported Media3 effect: ${effect.typeId}" }
            val evaluated = EffectRegistry.evaluate(effect, 0L)
            createOne(evaluated)?.let(::add)
        }
    }

    private fun createOne(effect: EffectInstance): Effect? = when (effect.typeId) {
        "localcut.color.brightness" -> {
            val amount = number(effect, "amount")
            val scale = (1.0 + amount).coerceAtLeast(0.0).toFloat()
            RgbAdjustment.Builder()
                .setRedScale(scale)
                .setGreenScale(scale)
                .setBlueScale(scale)
                .build()
        }
        "localcut.color.contrast" ->
            Contrast((number(effect, "amount") - 1.0).coerceIn(-1.0, 1.0).toFloat())
        "localcut.color.exposure" -> {
            val scale = 2.0.pow(number(effect, "stops")).toFloat()
            RgbAdjustment.Builder()
                .setRedScale(scale)
                .setGreenScale(scale)
                .setBlueScale(scale)
                .build()
        }
        "localcut.color.saturation" ->
            HslAdjustment.Builder()
                .adjustSaturation(((number(effect, "amount") - 1.0) * 100.0).coerceIn(-100.0, 100.0).toFloat())
                .build()
        "localcut.color.temperature" -> {
            val amount = number(effect, "amount").coerceIn(-1.0, 1.0)
            RgbAdjustment.Builder()
                .setRedScale((1.0 + 0.25 * amount).toFloat())
                .setGreenScale(1f)
                .setBlueScale((1.0 - 0.25 * amount).toFloat())
                .build()
        }
        "localcut.color.tint" -> {
            val amount = number(effect, "amount").coerceIn(-1.0, 1.0)
            RgbAdjustment.Builder()
                .setRedScale((1.0 + 0.125 * amount).toFloat())
                .setGreenScale((1.0 - 0.25 * amount).toFloat())
                .setBlueScale((1.0 + 0.125 * amount).toFloat())
                .build()
        }
        "localcut.blur.gaussian" -> {
            val radius = number(effect, "radius")
            if (radius <= 0.0) null else GaussianBlur(radius.toFloat())
        }
        else -> error("unsupported Media3 effect: ${effect.typeId}")
    }

    private fun number(effect: EffectInstance, name: String): Double =
        (effect.parameters.getValue(name) as EffectParameter.Number).value
}
