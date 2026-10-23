package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignCanvasSettings
import com.localcut.editor.domain.model.DesignElement
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

    fun mergeAdjacent(
        left: TextOverlay,
        right: TextOverlay,
        separator: String = " ",
    ): TextOverlay {
        require(left.startUs >= 0L && right.startUs >= 0L) { "caption timing must be non-negative" }
        require(left.durationUs > 0L && right.durationUs > 0L) { "caption duration must be positive" }
        require(left.startUs <= Long.MAX_VALUE - left.durationUs) { "left caption timing overflow" }
        val leftEndUs = left.startUs + left.durationUs
        require(leftEndUs == right.startUs) { "captions must be contiguous" }
        require(left.style == right.style) { "captions must share style before merge" }
        require(left.caption == right.caption) { "captions must share metadata before merge" }
        require(left.durationUs <= Long.MAX_VALUE - right.durationUs) { "merged caption duration overflow" }

        return left.copy(
            durationUs = left.durationUs + right.durationUs,
            text = listOf(left.text.trimEnd(), right.text.trimStart())
                .filter { it.isNotEmpty() }
                .joinToString(separator),
        )
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

object LayoutEngine {
    fun distributeHorizontally(elements: List<DesignElement>): List<DesignElement> {
        if (elements.size < 3) return elements
        require(elements.map { it.id }.distinct().size == elements.size) { "design element ids must be unique" }
        require(elements.all { it.transform.x.isFinite() }) { "design element x positions must be finite" }

        val sorted = elements.sortedWith(compareBy<DesignElement> { it.transform.x }.thenBy { it.id })
        val left = sorted.first().transform.x
        val right = sorted.last().transform.x
        val step = (right - left) / (sorted.size - 1).toDouble()
        val replacements = sorted.mapIndexed { index, element ->
            element.id to element.copy(
                transform = element.transform.copy(x = left + step * index.toDouble()),
            )
        }.toMap()
        return elements.map { replacements.getValue(it.id) }
    }
}
