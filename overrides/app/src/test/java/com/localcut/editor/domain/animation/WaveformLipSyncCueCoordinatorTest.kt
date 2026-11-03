package com.localcut.editor.domain.animation

import com.localcut.editor.audio.WaveformCache
import com.localcut.editor.audio.WaveformCacheKey
import com.localcut.editor.audio.WaveformData
import com.localcut.editor.audio.WaveformDecoder
import com.localcut.editor.audio.WaveformRepository
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformLipSyncCueCoordinatorTest {
    private val key = WaveformCacheKey("audio-fp", 0L, 800_000L, 8)
    private val speech = WaveformData(listOf(0.01f, 0.02f, 0.30f, 0.80f, 0.70f, 0.02f, 0.50f, 0.01f))

    @Test
    fun decodingAndCuePlanningAreBothDeferredOffCallerThread() {
        val decode = ManualExecutor()
        val plan = ManualExecutor()
        var decoderCalls = 0
        val repository = repository(decode) { _, _, _, _ ->
            decoderCalls++
            Result.success(speech)
        }
        val coordinator = WaveformLipSyncCueCoordinator(repository, plan)
        var delivered: Result<List<LipSyncCue>>? = null

        coordinator.request(key, "content://media/audio/1", 800_000L) { delivered = it }

        assertEquals(0, decoderCalls)
        assertNull(delivered)
        assertEquals(1, decode.pending)
        assertEquals(0, plan.pending)

        decode.runAll()
        assertEquals(1, decoderCalls)
        assertNull(delivered)
        assertEquals(1, plan.pending)

        plan.runAll()
        assertEquals(2, delivered!!.getOrThrow().size)
    }

    @Test
    fun cachedAudioStillPlansOffCallerAndDoesNotDecodeAgain() {
        val cache = WaveformCache(32).apply { put(key, speech) }
        val decode = ManualExecutor()
        val plan = ManualExecutor()
        var calls = 0
        val repository = WaveformRepository(cache, WaveformDecoder { _, _, _, _ ->
            calls++
            Result.failure(IllegalStateException("unexpected decode"))
        }, decode)
        var delivered: Result<List<LipSyncCue>>? = null

        WaveformLipSyncCueCoordinator(repository, plan)
            .request(key, "content://media/audio/1", 800_000L) { delivered = it }

        assertNull(delivered)
        assertEquals(0, decode.pending)
        assertEquals(1, plan.pending)
        plan.runAll()
        assertEquals(0, calls)
        assertEquals(2, delivered!!.getOrThrow().size)
    }

    @Test
    fun silentWaveformIsDeliveredAsEmptyCuesNotFabricatedSpeech() {
        val decode = ManualExecutor()
        val plan = ManualExecutor()
        val coordinator = WaveformLipSyncCueCoordinator(
            repository(decode) { _, _, _, _ -> Result.success(WaveformData(List(8) { 0.0f })) },
            plan,
        )
        var delivered: List<LipSyncCue>? = null

        coordinator.request(key, "content://media/audio/silence", 800_000L) {
            delivered = it.getOrThrow()
        }
        decode.runAll()
        plan.runAll()

        assertEquals(emptyList<LipSyncCue>(), delivered)
    }

    @Test
    fun cancelledRequestSuppressesStaleResultAfterDecode() {
        val decode = ManualExecutor()
        val plan = ManualExecutor()
        val coordinator = WaveformLipSyncCueCoordinator(
            repository(decode) { _, _, _, _ -> Result.success(speech) },
            plan,
        )
        var callbacks = 0

        val handle = coordinator.request(key, "content://media/audio/1", 800_000L) { callbacks++ }
        decode.runAll()
        assertEquals(1, plan.pending)
        handle.cancel()
        plan.runAll()
        assertEquals(0, callbacks)
        assertTrue(handle.isCancelled)
    }

    @Test
    fun cancelledRequestDoesNotPreventOtherSubscriberFromCompleting() {
        val decode = ManualExecutor()
        val plan = ManualExecutor()
        var calls = 0
        val coordinator = WaveformLipSyncCueCoordinator(
            repository(decode) { _, _, _, _ ->
                calls++
                Result.success(speech)
            },
            plan,
        )
        var firstCallbacks = 0
        var secondCallbacks = 0

        val first = coordinator.request(key, "content://media/audio/1", 800_000L) { firstCallbacks++ }
        coordinator.request(key, "content://media/audio/1", 800_000L) { secondCallbacks++ }
        first.cancel()
        decode.runAll()
        plan.runAll()

        assertEquals(1, calls)
        assertEquals(0, firstCallbacks)
        assertEquals(1, secondCallbacks)
    }

    @Test
    fun decodeFailureIsPropagatedWithoutCreatingCues() {
        val decode = ManualExecutor()
        val plan = ManualExecutor()
        val cause = IllegalStateException("codec failed")
        val coordinator = WaveformLipSyncCueCoordinator(
            repository(decode) { _, _, _, _ -> Result.failure(cause) },
            plan,
        )
        var delivered: Result<List<LipSyncCue>>? = null

        coordinator.request(key, "content://media/audio/bad", 800_000L) { delivered = it }
        decode.runAll()
        plan.runAll()

        assertTrue(delivered!!.isFailure)
        assertEquals(cause, delivered!!.exceptionOrNull())
    }

    @Test
    fun invalidDurationAndInvalidBucketCountFailWithoutDecoding() {
        val decode = ManualExecutor()
        val plan = ManualExecutor()
        val coordinator = WaveformLipSyncCueCoordinator(
            repository(decode) { _, _, _, _ -> Result.success(speech) },
            plan,
        )
        var badDuration: Result<List<LipSyncCue>>? = null
        var badBuckets: Result<List<LipSyncCue>>? = null

        coordinator.request(key, "content://media/audio/1", 0L) { badDuration = it }
        coordinator.request(key.copy(bucketCount = 0), "content://media/audio/1", 800_000L) {
            badBuckets = it
        }
        assertNull(badDuration)
        assertNull(badBuckets)
        assertEquals(0, decode.pending)
        plan.runAll()
        assertTrue(badDuration!!.isFailure)
        assertTrue(badBuckets!!.isFailure)
    }

    @Test
    fun planningExecutorRejectionIsDeliveredOnceAsFailure() {
        val cache = WaveformCache(32).apply { put(key, speech) }
        val repository = WaveformRepository(cache, WaveformDecoder { _, _, _, _ ->
            Result.failure(IllegalStateException("unreachable"))
        }, ManualExecutor())
        val coordinator = WaveformLipSyncCueCoordinator(
            repository,
            Executor { throw RejectedExecutionException("worker unavailable") },
        )
        var callbacks = 0
        var delivered: Result<List<LipSyncCue>>? = null

        coordinator.request(key, "content://media/audio/1", 800_000L) {
            callbacks++
            delivered = it
        }

        assertEquals(1, callbacks)
        assertTrue(delivered!!.exceptionOrNull() is RejectedExecutionException)
    }

    private fun repository(executor: Executor, decoder: WaveformDecoder): WaveformRepository =
        WaveformRepository(WaveformCache(64), decoder, executor)

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val pending: Int get() = tasks.size
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
