package com.localcut.editor.domain.animation

import com.localcut.editor.audio.WaveformCacheKey
import com.localcut.editor.audio.WaveformRepository
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Asynchronously converts a source-audio waveform into energy-based mouth cues.
 *
 * Both [WaveformRepository] and [planningExecutor] must use background executors in
 * production. This class does not mutate a project or perform Bridge writes: the
 * caller must recheck project revision, track locks and request ownership before
 * applying the returned cues. An empty successful list means no detected energy;
 * it must not be interpreted as permission to invent speech/mouth movement.
 */
class WaveformLipSyncCueCoordinator(
    private val waveformRepository: WaveformRepository,
    private val planningExecutor: Executor,
) {
    class RequestHandle internal constructor(private val cancelled: AtomicBoolean) {
        val isCancelled: Boolean get() = cancelled.get()
        fun cancel() {
            cancelled.set(true)
        }
    }

    fun request(
        key: WaveformCacheKey,
        uri: String,
        actionDurationUs: Long,
        callback: (Result<List<LipSyncCue>>) -> Unit,
    ): RequestHandle {
        val cancelled = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        val handle = RequestHandle(cancelled)

        fun deliver(result: Result<List<LipSyncCue>>) {
            if (!cancelled.get() && delivered.compareAndSet(false, true)) {
                callback(result)
            }
        }

        // Even cache hits go through this executor, so cue planning cannot
        // unexpectedly run on the UI/Bridge caller thread.
        fun schedule(work: () -> Result<List<LipSyncCue>>) {
            if (cancelled.get() || delivered.get()) return
            try {
                planningExecutor.execute {
                    if (cancelled.get() || delivered.get()) return@execute
                    val result = try {
                        work()
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    deliver(result)
                }
            } catch (error: Throwable) {
                // Executor rejection must not turn into a silent missing result.
                deliver(Result.failure(error))
            }
        }

        val inputError = runCatching {
            require(key.assetFingerprint.isNotBlank()) { "asset fingerprint must not be blank" }
            require(uri.isNotBlank()) { "audio URI must not be blank" }
            require(key.sourceInUs >= 0L && key.sourceOutUs > key.sourceInUs) {
                "source audio range must be positive"
            }
            require(key.bucketCount in 1..MAX_BUCKETS) { "waveform bucket count out of range" }
            require(actionDurationUs in 1L..MAX_ACTION_DURATION_US) {
                "action duration out of range"
            }
        }.exceptionOrNull()

        if (inputError != null) {
            schedule { Result.failure(inputError) }
            return handle
        }

        try {
            waveformRepository.request(key, uri) { decoded ->
                schedule {
                    decoded.fold(
                        onSuccess = { waveform ->
                            require(waveform.sampleCount == key.bucketCount) {
                                "decoded waveform bucket count does not match request"
                            }
                            Result.success(WaveformLipSyncCuePlanner.plan(waveform, actionDurationUs))
                        },
                        onFailure = { Result.failure(it) },
                    )
                }
            }
        } catch (error: Throwable) {
            schedule { Result.failure(error) }
        }

        return handle
    }

    private companion object {
        // Bounds both memory/CPU and the planner's integer bucket-boundary math.
        const val MAX_BUCKETS = 4_096
        const val MAX_ACTION_DURATION_US = Long.MAX_VALUE / MAX_BUCKETS
    }
}
