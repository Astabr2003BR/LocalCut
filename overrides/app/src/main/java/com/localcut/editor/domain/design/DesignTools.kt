package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.GuideAxis
import com.localcut.editor.domain.model.TextOverlay
import kotlin.math.abs

object CaptionEditor {
    fun split(
        overlay: TextOverlay,
        leftDurationUs: Long,
        rightId: String,
        leftText: String,
        rightText: String,
    ): Pair<TextOverlay, TextOverlay> {
        require(leftDurationUs > 0L && leftDurationUs < overlay.durationUs) {
            "caption split must be inside overlay duration"
        }
        require(rightId.isNotBlank()) { "right caption id must not be blank" }

        val left = overlay.copy(
            durationUs = leftDurationUs,
            text = leftText,
        )
        val right = overlay.copy(
            id = rightId,
            startUs = overlay.startUs + leftDurationUs,
            durationUs = overlay.durationUs - leftDurationUs,
            text = rightText,
        )
        return left to right
    }
}

object SnapEngine {
    fun snapX(valueNormalized: Double, settings: DesignCanvasSettings): Double =
        snap(valueNormalized, settings, GuideAxis.VERTICAL)

    fun snapY(valueNormalized: Double, settings: DesignCanvasSettings): Double =
        snap(valueNormalized, settings, GuideAxis.HORIZONTAL)

    private fun snap(
        valueNormalized: Double,
        settings: DesignCanvasSettings,
        guideAxis: GuideAxis,
    ): Double {
        if (!valueNormalized.isFinite()) return valueNormalized
        val tolerance = settings.snapToleranceNormalized
        if (!tolerance.isFinite() || tolerance < 0.0) return valueNormalized

        val candidates = buildList {
            add(0.0)
            add(0.5)
            add(1.0)
            settings.guides
                .asSequence()
                .filter { it.axis == guideAxis && it.positionNormalized.isFinite() }
                .forEach { add(it.positionNormalized) }
        }

        val nearest = candidates.minByOrNull { abs(it - valueNormalized) } ?: return valueNormalized
        return if (abs(nearest - valueNormalized) <= tolerance) nearest else valueNormalized
    }
}
