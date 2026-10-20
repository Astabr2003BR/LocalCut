package com.localcut.editor.audio

import java.util.LinkedHashMap

data class WaveformCacheKey(
    val assetFingerprint: String,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val bucketCount: Int,
)

data class WaveformData(
    val peaks: List<Float>,
) {
    val sampleCount: Int
        get() = peaks.size
}

class WaveformCache(
    private val maxSamples: Int,
) {
    private val entries = LinkedHashMap<WaveformCacheKey, WaveformData>(16, 0.75f, true)
    private var totalSamples = 0

    val currentSamples: Int
        @Synchronized get() = totalSamples

    @Synchronized
    operator fun get(key: WaveformCacheKey): WaveformData? = entries[key]

    @Synchronized
    fun put(key: WaveformCacheKey, data: WaveformData) {
        if (data.sampleCount > maxSamples) return

        entries.remove(key)?.let { previous ->
            totalSamples -= previous.sampleCount
        }
        entries[key] = data
        totalSamples += data.sampleCount

        val iterator = entries.entries.iterator()
        while (totalSamples > maxSamples && iterator.hasNext()) {
            val eldest = iterator.next()
            totalSamples -= eldest.value.sampleCount
            iterator.remove()
        }
    }
}
