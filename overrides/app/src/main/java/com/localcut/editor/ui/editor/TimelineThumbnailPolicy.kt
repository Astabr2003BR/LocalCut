package com.localcut.editor.ui.editor

import kotlin.math.ceil

object TimelineThumbnailPolicy {
    const val SAMPLE_WIDTH_DP = 64f
    const val MAX_SAMPLES_PER_CLIP = 6
    const val TARGET_WIDTH_PX = 144
    const val TARGET_HEIGHT_PX = 80

    fun sampleCount(clipWidthDp: Float): Int {
        if (!clipWidthDp.isFinite() || clipWidthDp <= 0f) return 1
        return ceil(clipWidthDp / SAMPLE_WIDTH_DP)
            .toInt()
            .coerceIn(1, MAX_SAMPLES_PER_CLIP)
    }

    fun sampleTimesUs(
        sourceInUs: Long,
        sourceOutUs: Long,
        clipWidthDp: Float,
        stillImage: Boolean,
    ): List<Long> {
        val safeStart = sourceInUs.coerceAtLeast(0L)
        if (stillImage || sourceOutUs <= safeStart) return listOf(safeStart)

        val count = sampleCount(clipWidthDp)
        val spanUs = sourceOutUs - safeStart
        return List(count) { index ->
            val numerator = (index * 2L) + 1L
            safeStart + (spanUs * numerator) / (count * 2L)
        }
    }

    fun cacheKey(
        assetFingerprint: String,
        sourceTimeUs: Long,
        widthPx: Int,
        heightPx: Int,
    ): String = buildString {
        append(assetFingerprint)
        append(':')
        append(sourceTimeUs.coerceAtLeast(0L))
        append(':')
        append(widthPx.coerceAtLeast(1))
        append('x')
        append(heightPx.coerceAtLeast(1))
    }
}
