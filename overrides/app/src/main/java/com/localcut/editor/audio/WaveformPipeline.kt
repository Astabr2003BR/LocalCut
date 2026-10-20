package com.localcut.editor.audio

import java.util.concurrent.Executor
import kotlin.math.abs

class WaveformAccumulator(
    private val sourceInUs: Long,
    private val sourceOutUs: Long,
    private val bucketCount: Int,
) {
    private val peaks: FloatArray

    init {
        require(sourceInUs >= 0L) { "sourceInUs must not be negative" }
        require(sourceOutUs > sourceInUs) { "sourceOutUs must be greater than sourceInUs" }
        require(bucketCount > 0) { "bucketCount must be positive" }
        peaks = FloatArray(bucketCount)
    }

    fun addPeakAt(timestampUs: Long, amplitude: Float) {
        if (timestampUs < sourceInUs || timestampUs >= sourceOutUs || !amplitude.isFinite()) return
        val durationUs = sourceOutUs - sourceInUs
        val relative = (timestampUs - sourceInUs).toDouble() / durationUs.toDouble()
        val bucket = (relative * bucketCount.toDouble()).toInt().coerceIn(0, bucketCount - 1)
        val normalized = abs(amplitude).coerceIn(0f, 1f)
        if (normalized > peaks[bucket]) peaks[bucket] = normalized
    }

    fun toData(): WaveformData = WaveformData(peaks.toList())
}

fun interface WaveformDecoder {
    fun decode(
        uri: String,
        sourceInUs: Long,
        sourceOutUs: Long,
        bucketCount: Int,
    ): Result<WaveformData>
}

class WaveformRepository(
    private val cache: WaveformCache,
    private val decoder: WaveformDecoder,
    private val executor: Executor,
) {
    private val lock = Any()
    private val inFlight = mutableMapOf<WaveformCacheKey, MutableList<(Result<WaveformData>) -> Unit>>()

    fun request(
        key: WaveformCacheKey,
        uri: String,
        callback: (Result<WaveformData>) -> Unit,
    ) {
        cache[key]?.let { cached ->
            callback(Result.success(cached))
            return
        }

        val shouldSchedule = synchronized(lock) {
            val callbacks = inFlight[key]
            if (callbacks != null) {
                callbacks += callback
                false
            } else {
                inFlight[key] = mutableListOf(callback)
                true
            }
        }
        if (!shouldSchedule) return

        try {
            executor.execute {
                val result = try {
                    decoder.decode(uri, key.sourceInUs, key.sourceOutUs, key.bucketCount)
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                result.getOrNull()?.let { cache.put(key, it) }
                val callbacks = synchronized(lock) { inFlight.remove(key).orEmpty() }
                callbacks.forEach { it(result) }
            }
        } catch (error: Throwable) {
            val callbacks = synchronized(lock) { inFlight.remove(key).orEmpty() }
            val result = Result.failure<WaveformData>(error)
            callbacks.forEach { it(result) }
        }
    }
}
