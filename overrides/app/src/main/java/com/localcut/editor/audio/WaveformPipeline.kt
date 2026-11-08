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

    private class Subscriber(val callback: (Result<WaveformData>) -> Unit) {
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    private class InFlight(val subscribers: MutableList<Subscriber>) {
        var worker: Thread? = null
    }

    /** Unsubscribe only this caller; stop decoding when the last subscriber cancels. */
    class Subscription internal constructor(private val cancelAction: () -> Unit) {
        private val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)

        fun cancel() {
            if (cancelled.compareAndSet(false, true)) cancelAction()
        }
    }

    private val inFlight = mutableMapOf<WaveformCacheKey, InFlight>()

    /** Existing callers retain the original callback-only API. */
    fun request(key: WaveformCacheKey, uri: String, callback: (Result<WaveformData>) -> Unit) {
        requestCancellable(key, uri, callback)
    }

    fun requestCancellable(
        key: WaveformCacheKey,
        uri: String,
        callback: (Result<WaveformData>) -> Unit,
    ): Subscription {
        val subscriber = Subscriber(callback)
        var cached: WaveformData? = null
        var job: InFlight? = null
        var shouldSchedule = false

        synchronized(lock) {
            // Recheck under the same lock used at completion to avoid duplicate decoding
            // when an older request finishes between the cache lookup and subscription.
            cached = cache[key]
            if (cached == null) {
                val existing = inFlight[key]
                if (existing != null) {
                    existing.subscribers.add(subscriber)
                    job = existing
                } else {
                    val created = InFlight(mutableListOf(subscriber))
                    inFlight[key] = created
                    job = created
                    shouldSchedule = true
                }
            }
        }

        cached?.let {
            callback(Result.success(it))
            return Subscription {}
        }

        val activeJob = checkNotNull(job)
        val subscription = Subscription {
            val workerToInterrupt = synchronized(lock) {
                subscriber.cancelled.set(true)
                if (inFlight[key] !== activeJob) {
                    null
                } else {
                    activeJob.subscribers.remove(subscriber)
                    if (activeJob.subscribers.isEmpty()) {
                        inFlight.remove(key)
                        activeJob.worker
                    } else null
                }
            }
            // Do not interrupt shared decoding while another subscriber still needs it.
            workerToInterrupt?.interrupt()
        }

        if (shouldSchedule) {
            try {
                executor.execute {
                    val shouldDecode = synchronized(lock) {
                        if (inFlight[key] !== activeJob || activeJob.subscribers.isEmpty()) {
                            false
                        } else {
                            activeJob.worker = Thread.currentThread()
                            true
                        }
                    }
                    if (!shouldDecode) return@execute

                    val result = try {
                        decoder.decode(uri, key.sourceInUs, key.sourceOutUs, key.bucketCount)
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    val subscribers = synchronized(lock) {
                        if (inFlight[key] === activeJob) {
                            result.getOrNull()?.let { cache.put(key, it) }
                            inFlight.remove(key)
                            activeJob.worker = null
                            activeJob.subscribers.toList()
                        } else {
                            emptyList()
                        }
                    }
                    subscribers.forEach { listener ->
                        if (!listener.cancelled.get()) listener.callback(result)
                    }
                }
            } catch (error: Throwable) {
                val subscribers = synchronized(lock) {
                    if (inFlight[key] === activeJob) {
                        inFlight.remove(key)
                        activeJob.subscribers.toList()
                    } else {
                        emptyList()
                    }
                }
                subscribers.forEach { listener ->
                    if (!listener.cancelled.get()) listener.callback(Result.failure(error))
                }
            }
        }
        return subscription
    }
}
