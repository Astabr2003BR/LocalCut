package com.localcut.editor.media

import com.localcut.editor.domain.animation.PropertyAnimationEvaluator
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TextAnimationDescriptor
import com.localcut.editor.domain.model.TextAnimationKind
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

data class TextOverlayMotionState(
    val visibleText: String,
    val alpha: Float,
    val anchorX: Float,
    val anchorY: Float,
    val scaleMultiplier: Float,
    val rotationOffsetDeg: Float,
)

object TextOverlayMotionEvaluator {
    fun evaluate(
        text: String,
        startUs: Long,
        endUs: Long,
        presentationTimeUs: Long,
        baseOpacity: Float = 1f,
        baseAnchorX: Float = 0f,
        baseAnchorY: Float = 0f,
        entrance: TextAnimationDescriptor = TextAnimationDescriptor(),
        exit: TextAnimationDescriptor = TextAnimationDescriptor(),
        loop: TextAnimationDescriptor = TextAnimationDescriptor(),
        perCharacterDelayUs: Long = 0L,
    ): TextOverlayMotionState {
        require(endUs > startUs) { "text overlay end must be after start" }
        require(baseOpacity.isFinite() && baseOpacity in 0f..1f) { "base opacity must be in 0..1" }
        require(baseAnchorX.isFinite() && baseAnchorY.isFinite()) { "text anchors must be finite" }
        require(perCharacterDelayUs >= 0L) { "per-character delay must be non-negative" }
        val descriptors = listOf(entrance, exit, loop)
        descriptors.forEach { descriptor ->
            val issues = descriptor.validationIssues()
            require(issues.isEmpty()) { "invalid text animation: ${issues.first()}" }
        }

        if (presentationTimeUs < startUs || presentationTimeUs >= endUs) {
            return TextOverlayMotionState(
                visibleText = "",
                alpha = 0f,
                anchorX = baseAnchorX,
                anchorY = baseAnchorY,
                scaleMultiplier = 1f,
                rotationOffsetDeg = 0f,
            )
        }

        val localUs = presentationTimeUs - startUs
        val durationUs = endUs - startUs
        val entranceProgress = descriptorProgress(entrance, localUs)
        val exitStartUs = (durationUs - exit.durationUs).coerceAtLeast(0L)
        val exitProgress = if (exit.kind == TextAnimationKind.NONE || exit.durationUs <= 0L || localUs < exitStartUs) {
            0.0
        } else {
            descriptorProgress(exit, localUs - exitStartUs)
        }
        val loopProgress = loopProgress(loop, localUs)

        var alphaMultiplier = 1.0
        var xOffset = 0.0
        var yOffset = 0.0
        var scaleMultiplier = 1.0
        var rotationOffsetDeg = 0.0
        var visibleText = text

        if (entrance.kind != TextAnimationKind.NONE && entrance.durationUs > 0L && localUs <= entrance.durationUs) {
            when (entrance.kind) {
                TextAnimationKind.FADE -> alphaMultiplier *= entranceProgress
                TextAnimationKind.SLIDE_UP -> yOffset -= 0.20 * entrance.amount * (1.0 - entranceProgress)
                TextAnimationKind.SLIDE_DOWN -> yOffset += 0.20 * entrance.amount * (1.0 - entranceProgress)
                TextAnimationKind.SLIDE_LEFT -> xOffset -= 0.20 * entrance.amount * (1.0 - entranceProgress)
                TextAnimationKind.SLIDE_RIGHT -> xOffset += 0.20 * entrance.amount * (1.0 - entranceProgress)
                TextAnimationKind.POP -> scaleMultiplier *= 0.65 + 0.35 * entranceProgress
                TextAnimationKind.SCALE -> scaleMultiplier *= 0.50 + 0.50 * entranceProgress
                TextAnimationKind.TYPEWRITER -> visibleText = typewriterText(
                    text = text,
                    localUs = localUs,
                    durationUs = entrance.durationUs,
                    progress = entranceProgress,
                    perCharacterDelayUs = perCharacterDelayUs,
                )
                TextAnimationKind.BOUNCE -> {
                    scaleMultiplier *= 0.75 + 0.25 * entranceProgress
                    yOffset -= sin(entranceProgress * PI) * 0.04 * entrance.amount
                }
                TextAnimationKind.WIGGLE -> rotationOffsetDeg += sin(entranceProgress * PI * 4.0) * 6.0 * entrance.amount * (1.0 - entranceProgress)
                TextAnimationKind.NONE -> Unit
            }
        }

        if (exit.kind != TextAnimationKind.NONE && exit.durationUs > 0L && localUs >= exitStartUs) {
            when (exit.kind) {
                TextAnimationKind.FADE -> alphaMultiplier *= 1.0 - exitProgress
                TextAnimationKind.SLIDE_UP -> yOffset -= 0.20 * exit.amount * exitProgress
                TextAnimationKind.SLIDE_DOWN -> yOffset += 0.20 * exit.amount * exitProgress
                TextAnimationKind.SLIDE_LEFT -> xOffset -= 0.20 * exit.amount * exitProgress
                TextAnimationKind.SLIDE_RIGHT -> xOffset += 0.20 * exit.amount * exitProgress
                TextAnimationKind.POP -> scaleMultiplier *= 1.0 - 0.35 * exitProgress
                TextAnimationKind.SCALE -> scaleMultiplier *= 1.0 - 0.50 * exitProgress
                TextAnimationKind.TYPEWRITER -> visibleText = typewriterExitText(text, exitProgress)
                TextAnimationKind.BOUNCE -> {
                    scaleMultiplier *= 1.0 - 0.25 * exitProgress
                    yOffset += sin(exitProgress * PI) * 0.04 * exit.amount
                }
                TextAnimationKind.WIGGLE -> rotationOffsetDeg += sin(exitProgress * PI * 4.0) * 6.0 * exit.amount * exitProgress
                TextAnimationKind.NONE -> Unit
            }
        }

        if (loop.kind != TextAnimationKind.NONE && loop.durationUs > 0L) {
            when (loop.kind) {
                TextAnimationKind.FADE -> alphaMultiplier *= 0.75 + 0.25 * sin(loopProgress * 2.0 * PI).let { (it + 1.0) * 0.5 }
                TextAnimationKind.SLIDE_UP -> yOffset -= sin(loopProgress * 2.0 * PI) * 0.03 * loop.amount
                TextAnimationKind.SLIDE_DOWN -> yOffset += sin(loopProgress * 2.0 * PI) * 0.03 * loop.amount
                TextAnimationKind.SLIDE_LEFT -> xOffset -= sin(loopProgress * 2.0 * PI) * 0.03 * loop.amount
                TextAnimationKind.SLIDE_RIGHT -> xOffset += sin(loopProgress * 2.0 * PI) * 0.03 * loop.amount
                TextAnimationKind.POP,
                TextAnimationKind.SCALE,
                TextAnimationKind.BOUNCE -> scaleMultiplier *= 1.0 + sin(loopProgress * 2.0 * PI) * 0.06 * loop.amount
                TextAnimationKind.WIGGLE -> rotationOffsetDeg += sin(loopProgress * 2.0 * PI) * 6.0 * loop.amount
                TextAnimationKind.TYPEWRITER,
                TextAnimationKind.NONE -> Unit
            }
        }

        return TextOverlayMotionState(
            visibleText = visibleText,
            alpha = (baseOpacity * alphaMultiplier.toFloat()).coerceIn(0f, 1f),
            anchorX = (baseAnchorX + xOffset.toFloat()).coerceIn(-1f, 1f),
            anchorY = (baseAnchorY + yOffset.toFloat()).coerceIn(-1f, 1f),
            scaleMultiplier = scaleMultiplier.toFloat().coerceAtLeast(0.01f),
            rotationOffsetDeg = rotationOffsetDeg.toFloat(),
        )
    }

    private fun descriptorProgress(descriptor: TextAnimationDescriptor, elapsedUs: Long): Double {
        if (descriptor.durationUs <= 0L) return 1.0
        val clampedElapsed = elapsedUs.coerceIn(0L, descriptor.durationUs)
        val animation = PropertyAnimation(
            propertyId = "text.animation.progress",
            keyframes = listOf(
                PropertyKeyframe(
                    id = "start",
                    offsetUs = 0L,
                    value = PropertyValue.Scalar(0.0),
                    easingToNext = descriptor.easing,
                ),
                PropertyKeyframe(
                    id = "end",
                    offsetUs = descriptor.durationUs,
                    value = PropertyValue.Scalar(1.0),
                    easingToNext = Easing(),
                ),
            ),
        )
        return (PropertyAnimationEvaluator.valueAt(animation, clampedElapsed) as PropertyValue.Scalar).value
    }

    private fun loopProgress(loop: TextAnimationDescriptor, localUs: Long): Double {
        if (loop.durationUs <= 0L) return 0.0
        val phaseUs = localUs % loop.durationUs
        return phaseUs.toDouble() / loop.durationUs.toDouble()
    }

    private fun typewriterText(
        text: String,
        localUs: Long,
        durationUs: Long,
        progress: Double,
        perCharacterDelayUs: Long,
    ): String {
        if (text.isEmpty()) return text
        val count = if (perCharacterDelayUs > 0L) {
            ((localUs / perCharacterDelayUs) + 1L).coerceAtMost(text.length.toLong()).toInt()
        } else {
            ceil(progress.coerceIn(0.0, 1.0) * text.length.toDouble()).toInt().coerceIn(0, text.length)
        }
        return text.take(count)
    }

    private fun typewriterExitText(text: String, progress: Double): String {
        if (text.isEmpty()) return text
        val remaining = ceil((1.0 - progress.coerceIn(0.0, 1.0)) * text.length.toDouble())
            .toInt()
            .coerceIn(0, text.length)
        return text.take(remaining)
    }
}
