@file:OptIn(
    androidx.media3.common.util.ExperimentalApi::class,
    androidx.media3.common.util.UnstableApi::class,
)

package com.localcut.editor.media

import android.content.Context
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import androidx.annotation.RequiresApi
import androidx.media3.common.Effect
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.video.AsyncFrame
import androidx.media3.common.video.Frame
import androidx.media3.common.video.FrameProcessor
import androidx.media3.common.video.HardwareBufferFrame
import androidx.media3.common.video.SyncFenceWrapper
import androidx.media3.effect.DefaultGlFrameProcessor
import androidx.media3.effect.DefaultGlObjectsProvider
import androidx.media3.effect.HardwareBufferJni
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared blend backend for CompositionPlayer and Transformer.
 *
 * Existing Media3 effects remain on the official [DefaultGlFrameProcessor]: item effects are
 * applied before LocalCut's pixel compositor and composition effects are applied afterwards.
 */
@RequiresApi(26)
internal class LocalCutBlendFrameProcessor private constructor(
    context: Context,
    private val output: androidx.media3.common.video.FrameWriter,
    private val listenerExecutor: Executor,
    private val listener: FrameProcessor.Listener,
) : FrameProcessor {
    class Factory(context: Context) : FrameProcessor.Factory {
        private val appContext = context.applicationContext

        override fun create(
            output: androidx.media3.common.video.FrameWriter,
            listenerExecutor: Executor,
            listener: FrameProcessor.Listener,
        ): FrameProcessor = LocalCutBlendFrameProcessor(
            context = appContext,
            output = output,
            listenerExecutor = listenerExecutor,
            listener = listener,
        )
    }

    private val appContext = context.applicationContext
    private val hardwareBufferJni = HardwareBufferJni.INSTANCE
    private val glObjectsProvider: GlObjectsProvider = DefaultGlObjectsProvider()
    private val rawGlExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LocalCutBlendGL")
    }
    private val glExecutor: ListeningExecutorService = MoreExecutors.listeningDecorator(rawGlExecutor)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LocalCutBlendWorker")
    }
    private val preWriter = LocalCutIntermediateFrameWriter()
    private val preWakeup = Semaphore(0)
    private val postWakeup = Semaphore(0)
    private val originalByProxy = Collections.synchronizedMap(IdentityHashMap<Frame, Frame>())
    private val queuedOriginals = ConcurrentHashMap.newKeySet<Frame>()
    private val terminalError = AtomicReference<VideoFrameProcessingException?>(null)
    private val busy = AtomicBoolean(false)
    private val wakeupRequested = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private lateinit var eglDisplay: EGLDisplay
    private lateinit var eglSurface: EGLSurface
    private lateinit var blendRenderer: LocalCutHardwareBufferBlendRenderer
    private val preProcessor: FrameProcessor
    private val postProcessor: FrameProcessor

    init {
        glExecutor.submit {
            eglDisplay = GlUtil.getDefaultEglDisplay()
            val eglContext = glObjectsProvider.createEglContext(
                eglDisplay,
                3,
                GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888,
            )
            eglSurface = glObjectsProvider.createFocusedPlaceholderEglSurface(eglContext, eglDisplay)
            blendRenderer = LocalCutHardwareBufferBlendRenderer(eglDisplay, hardwareBufferJni)
        }.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val preFactory = DefaultGlFrameProcessor.Factory.Builder(appContext)
            .setHardwareBufferJniWrapper(hardwareBufferJni)
            .setGlObjectsProviderAndExecutorService(glObjectsProvider, glExecutor)
            .build()
        preProcessor = preFactory.create(
            preWriter,
            listenerExecutor,
            object : FrameProcessor.Listener {
                override fun onWakeup() {
                    preWakeup.release()
                }

                override fun onError(exception: VideoFrameProcessingException) {
                    registerError(exception)
                }

                override fun onFrameProcessed(frame: Frame, onCompleteFence: SyncFenceWrapper?) {
                    val original = synchronized(originalByProxy) { originalByProxy.remove(frame) }
                    if (original != null) {
                        queuedOriginals.remove(original)
                        listener.onFrameProcessed(original, onCompleteFence)
                    } else {
                        onCompleteFence?.close()
                    }
                }
            },
        )

        val postFactory = DefaultGlFrameProcessor.Factory.Builder(appContext)
            .setHardwareBufferJniWrapper(hardwareBufferJni)
            .setGlObjectsProviderAndExecutorService(glObjectsProvider, glExecutor)
            .build()
        postProcessor = postFactory.create(
            output,
            listenerExecutor,
            object : FrameProcessor.Listener {
                override fun onWakeup() {
                    postWakeup.release()
                }

                override fun onError(exception: VideoFrameProcessingException) {
                    registerError(exception)
                }

                override fun onFrameProcessed(frame: Frame, onCompleteFence: SyncFenceWrapper?) {
                    try {
                        if (onCompleteFence != null) {
                            check(onCompleteFence.awaitMs(FENCE_TIMEOUT_MS)) {
                                "BLEND_POSTPROCESS_FENCE_TIMEOUT"
                            }
                        }
                    } catch (error: Throwable) {
                        registerError(asVideoFrameProcessingException(error))
                    } finally {
                        onCompleteFence?.close()
                        (frame as? HardwareBufferFrame)?.hardwareBuffer?.close()
                    }
                }
            },
        )
    }

    override fun queue(frames: List<AsyncFrame>): Boolean {
        check(!closed.get()) { "BLEND_FRAME_PROCESSOR_CLOSED" }
        require(frames.isNotEmpty()) { "BLEND_INPUTS_EMPTY" }
        terminalError.get()?.let { throw IllegalStateException("BLEND_FRAME_PROCESSOR_FAILED", it) }
        if (!busy.compareAndSet(false, true)) {
            wakeupRequested.set(true)
            return false
        }

        worker.execute { processBatch(frames) }
        return true
    }

    override fun signalEndOfStream() {
        if (closed.get()) return
        worker.execute {
            try {
                preProcessor.signalEndOfStream()
                postProcessor.signalEndOfStream()
            } catch (error: Throwable) {
                registerError(asVideoFrameProcessingException(error))
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.shutdownNow()
        preWakeup.release()
        postWakeup.release()

        runCatching { preProcessor.close() }
        runCatching { postProcessor.close() }
        runCatching { preWriter.close() }

        runCatching {
            glExecutor.submit {
                runCatching { blendRenderer.release() }
                runCatching { GlUtil.destroyEglSurface(eglDisplay, eglSurface) }
                runCatching { glObjectsProvider.release(eglDisplay) }
            }.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        glExecutor.shutdownNow()
        releaseUnprocessedOriginals()
    }

    private fun processBatch(frames: List<AsyncFrame>) {
        val preprocessed = mutableListOf<AsyncFrame>()
        try {
            checkNoTerminalError()
            frames.forEach { asyncFrame ->
                preprocessed += preprocessOne(asyncFrame)
                checkNoTerminalError()
            }

            val blended = glExecutor.submit<HardwareBufferFrame> {
                blendRenderer.compose(preprocessed)
            }.get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            closePreprocessed(preprocessed)
            preprocessed.clear()
            checkNoTerminalError()

            queueWithBackpressure(
                processor = postProcessor,
                frames = listOf(AsyncFrame(blended, null)),
                wakeup = postWakeup,
                label = "BLEND_POSTPROCESS_BACKPRESSURE_TIMEOUT",
            )
        } catch (error: Throwable) {
            closePreprocessed(preprocessed)
            releaseUnqueuedOriginals(frames)
            registerError(asVideoFrameProcessingException(error))
        } finally {
            busy.set(false)
            if (wakeupRequested.getAndSet(false) && !closed.get()) {
                listenerExecutor.execute { listener.onWakeup() }
            }
        }
    }

    private fun preprocessOne(asyncFrame: AsyncFrame): AsyncFrame {
        val original = asyncFrame.frame as? HardwareBufferFrame
            ?: throw IllegalArgumentException("BLEND_INPUT_MUST_USE_HARDWARE_BUFFER")
        val preProcessorMetadata = HashMap<String, Any>(original.metadata)
        preProcessorMetadata[DefaultGlFrameProcessor.KEY_COMPOSITION_EFFECTS] = ImmutableList.of<Effect>()
        // Keep the real compositor settings in metadata. With one frame the official compositor
        // does not combine layers, and LocalCut needs these settings afterwards to resolve alpha
        // and blend at the exact composition timestamp.
        val proxy = original.buildUpon().setMetadata(preProcessorMetadata).build()
        synchronized(originalByProxy) {
            originalByProxy[proxy] = original
        }
        queuedOriginals.add(original)

        try {
            queueWithBackpressure(
                processor = preProcessor,
                frames = listOf(AsyncFrame(proxy, asyncFrame.acquireFence)),
                wakeup = preWakeup,
                label = "BLEND_PREPROCESS_BACKPRESSURE_TIMEOUT",
            )
        } catch (error: Throwable) {
            synchronized(originalByProxy) { originalByProxy.remove(proxy) }
            queuedOriginals.remove(original)
            throw error
        }

        val processed = preWriter.takeOutput()
        val processedFrame = processed.frame as? HardwareBufferFrame
            ?: throw IllegalStateException("BLEND_PREPROCESS_OUTPUT_MUST_USE_HARDWARE_BUFFER")
        // DefaultGlFrameProcessor writes pixels and contentTimeUs to its FrameWriter output but
        // does not propagate the input metadata. Restore the unmodified original metadata here:
        // item effects were already applied by preProcessor, while composition effects must remain
        // available for postProcessor after LocalCut performs the blend.
        val frameWithMetadata = processedFrame
            .buildUpon()
            .setMetadata(original.metadata)
            .setContentTimeUs(processedFrame.contentTimeUs)
            .build()
        return AsyncFrame(frameWithMetadata, processed.acquireFence)
    }

    private fun queueWithBackpressure(
        processor: FrameProcessor,
        frames: List<AsyncFrame>,
        wakeup: Semaphore,
        label: String,
    ) {
        while (!processor.queue(frames)) {
            check(!closed.get()) { "BLEND_FRAME_PROCESSOR_CLOSED" }
            checkNoTerminalError()
            check(wakeup.tryAcquire(BACKPRESSURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) { label }
        }
    }

    private fun closePreprocessed(frames: List<AsyncFrame>) {
        frames.forEach { async ->
            async.acquireFence?.close()
            (async.frame as? HardwareBufferFrame)?.hardwareBuffer?.close()
        }
    }

    private fun releaseUnqueuedOriginals(frames: List<AsyncFrame>) {
        frames.forEach { async ->
            val frame = async.frame
            if (!queuedOriginals.contains(frame)) {
                async.acquireFence?.close()
                listenerExecutor.execute { listener.onFrameProcessed(frame, null) }
            }
        }
    }

    private fun releaseUnprocessedOriginals() {
        val frames = queuedOriginals.toList()
        queuedOriginals.clear()
        frames.forEach { frame ->
            listenerExecutor.execute { listener.onFrameProcessed(frame, null) }
        }
        synchronized(originalByProxy) { originalByProxy.clear() }
    }

    private fun checkNoTerminalError() {
        terminalError.get()?.let { throw it }
    }

    private fun registerError(exception: VideoFrameProcessingException) {
        if (terminalError.compareAndSet(null, exception)) {
            preWakeup.release()
            postWakeup.release()
            listenerExecutor.execute { listener.onError(exception) }
        }
    }

    private fun asVideoFrameProcessingException(error: Throwable): VideoFrameProcessingException =
        when (error) {
            is VideoFrameProcessingException -> error
            is java.util.concurrent.ExecutionException -> {
                val cause = error.cause ?: error
                if (cause is VideoFrameProcessingException) cause else VideoFrameProcessingException(cause)
            }
            else -> VideoFrameProcessingException(error)
        }

    private companion object {
        const val INIT_TIMEOUT_SECONDS = 10L
        const val PROCESS_TIMEOUT_SECONDS = 30L
        const val CLOSE_TIMEOUT_SECONDS = 10L
        const val BACKPRESSURE_TIMEOUT_MS = 5_000L
        const val FENCE_TIMEOUT_MS = 5_000L
    }
}
