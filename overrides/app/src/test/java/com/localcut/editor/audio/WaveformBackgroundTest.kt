package com.localcut.editor.audio

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformBackgroundTest {
    @Test
    fun accumulatorBucketsAbsolutePeakBySourceTimestamp() {
        val accumulator = WaveformAccumulator(
            sourceInUs = 1_000_000L,
            sourceOutUs = 5_000_000L,
            bucketCount = 4,
        )

        accumulator.addPeakAt(1_000_000L, -0.25f)
        accumulator.addPeakAt(1_900_000L, 0.80f)
        accumulator.addPeakAt(2_500_000L, -0.50f)
        accumulator.addPeakAt(3_500_000L, 1.50f)
        accumulator.addPeakAt(4_999_999L, -0.70f)
        accumulator.addPeakAt(5_000_000L, 1.0f) // End is exclusive.

        assertEquals(listOf(0.80f, 0.50f, 1.0f, 0.70f), accumulator.toData().peaks)
    }

    @Test
    fun repositoryRunsDecodeOffCallerAndDeduplicatesSameInFlightKey() {
        val executor = ManualExecutor()
        val decoder = CountingDecoder(Result.success(WaveformData(listOf(0.2f, 0.9f))))
        val repository = WaveformRepository(
            cache = WaveformCache(maxSamples = 32),
            decoder = decoder,
            executor = executor,
        )
        val key = WaveformCacheKey("asset-fp", 0L, 2_000_000L, 2)
        var first: Result<WaveformData>? = null
        var second: Result<WaveformData>? = null

        repository.request(key, "content://audio/1") { first = it }
        repository.request(key, "content://audio/1") { second = it }

        assertNull(first)
        assertNull(second)
        assertEquals(0, decoder.calls)
        assertEquals(1, executor.pending)

        executor.runAll()

        assertEquals(1, decoder.calls)
        assertEquals(listOf(0.2f, 0.9f), first!!.getOrThrow().peaks)
        assertEquals(listOf(0.2f, 0.9f), second!!.getOrThrow().peaks)
    }

    @Test
    fun cachedWaveformReturnsWithoutSchedulingAnotherDecode() {
        val executor = ManualExecutor()
        val cache = WaveformCache(maxSamples = 32)
        val key = WaveformCacheKey("asset-fp", 0L, 1_000_000L, 1)
        val cached = WaveformData(listOf(0.6f))
        cache.put(key, cached)
        val decoder = CountingDecoder(Result.success(WaveformData(listOf(0.1f))))
        val repository = WaveformRepository(cache, decoder, executor)
        var delivered: WaveformData? = null

        repository.request(key, "content://audio/1") { delivered = it.getOrThrow() }

        assertEquals(cached, delivered)
        assertEquals(0, decoder.calls)
        assertEquals(0, executor.pending)
    }

    @Test
    fun failedDecodeIsNotCachedAndCanBeRetried() {
        val executor = ManualExecutor()
        val decoder = SequenceDecoder(
            ArrayDeque(
                listOf(
                    Result.failure(IllegalStateException("decode failed")),
                    Result.success(WaveformData(listOf(0.4f))),
                ),
            ),
        )
        val repository = WaveformRepository(WaveformCache(8), decoder, executor)
        val key = WaveformCacheKey("retry", 0L, 1_000_000L, 1)
        var firstFailed = false
        var secondSucceeded = false

        repository.request(key, "content://audio/retry") { firstFailed = it.isFailure }
        executor.runAll()
        repository.request(key, "content://audio/retry") { secondSucceeded = it.getOrNull()?.peaks == listOf(0.4f) }
        executor.runAll()

        assertTrue(firstFailed)
        assertTrue(secondSucceeded)
        assertEquals(2, decoder.calls)
    }

    @Test
    fun lastSubscriberCancellationBeforeExecutionSkipsDecodeAndAllowsRetry() {
        val executor = ManualExecutor()
        val decoder = CountingDecoder(Result.success(WaveformData(listOf(0.5f))))
        val repository = WaveformRepository(WaveformCache(8), decoder, executor)
        val key = WaveformCacheKey("cancel-before-start", 0L, 1_000_000L, 1)
        var cancelledCallbacks = 0

        val old = repository.requestCancellable(key, "content://audio/test") {
            cancelledCallbacks++
        }
        old.cancel()
        old.cancel() // Idempotent.
        executor.runAll()

        assertEquals(0, decoder.calls)
        assertEquals(0, cancelledCallbacks)

        var retry: WaveformData? = null
        repository.request(key, "content://audio/test") { retry = it.getOrThrow() }
        executor.runAll()
        assertEquals(1, decoder.calls)
        assertEquals(listOf(0.5f), retry!!.peaks)
    }

    @Test
    fun cancellingOneSubscriberPreservesSharedDecode() {
        val executor = ManualExecutor()
        val decoder = CountingDecoder(Result.success(WaveformData(listOf(0.7f))))
        val repository = WaveformRepository(WaveformCache(8), decoder, executor)
        val key = WaveformCacheKey("shared-decode", 0L, 1_000_000L, 1)
        var cancelledCallbacks = 0
        var survivingCallbacks = 0

        val first = repository.requestCancellable(key, "content://audio/1") {
            cancelledCallbacks++
        }
        repository.requestCancellable(key, "content://audio/1") {
            survivingCallbacks++
            assertEquals(listOf(0.7f), it.getOrThrow().peaks)
        }
        first.cancel()
        executor.runAll()

        assertEquals(1, decoder.calls)
        assertEquals(0, cancelledCallbacks)
        assertEquals(1, survivingCallbacks)
    }

    @Test
    fun cancellingLastSubscriberInterruptsRunningDecodeAndAllowsRetry() {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val started = java.util.concurrent.CountDownLatch(1)
        val interrupted = java.util.concurrent.CountDownLatch(1)
        val decoder = WaveformDecoder { _, _, _, _ ->
            started.countDown()
            try {
                java.util.concurrent.CountDownLatch(1).await()
                Result.success(WaveformData(listOf(0.1f)))
            } catch (error: InterruptedException) {
                interrupted.countDown()
                Result.failure(error)
            }
        }
        val repository = WaveformRepository(WaveformCache(8), decoder, executor)
        val key = WaveformCacheKey("running-cancel", 0L, 1_000_000L, 1)
        var cancelledCallbacks = 0
        try {
            val handle = repository.requestCancellable(key, "content://audio/1") {
                cancelledCallbacks++
            }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            handle.cancel()
            assertTrue(interrupted.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(0, cancelledCallbacks)
        } finally {
            executor.shutdownNow()
        }
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val pending: Int get() = tasks.size
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private class CountingDecoder(private val result: Result<WaveformData>) : WaveformDecoder {
        var calls = 0
        override fun decode(uri: String, sourceInUs: Long, sourceOutUs: Long, bucketCount: Int): Result<WaveformData> {
            calls += 1
            return result
        }
    }

    private class SequenceDecoder(private val results: ArrayDeque<Result<WaveformData>>) : WaveformDecoder {
        var calls = 0
        override fun decode(uri: String, sourceInUs: Long, sourceOutUs: Long, bucketCount: Int): Result<WaveformData> {
            calls += 1
            return results.removeFirst()
        }
    }
}
