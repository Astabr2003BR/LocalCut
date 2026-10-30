@file:OptIn(androidx.media3.common.util.ExperimentalApi::class)

package com.localcut.editor.media

import android.hardware.HardwareBuffer
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.video.AsyncFrame
import androidx.media3.common.video.Frame
import androidx.media3.common.video.FrameWriter
import androidx.media3.common.video.HardwareBufferFrame
import androidx.media3.common.video.SyncFenceWrapper
import com.google.common.collect.ImmutableMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Single-slot GPU writer used only inside LocalCut's pixel compositor.
 *
 * The Media3 FrameProcessor contract owns the dequeued frame until it queues it back. After
 * [takeOutput] returns, ownership moves to the caller, which must close the HardwareBuffer when the
 * next GPU stage is finished with it.
 */
@RequiresApi(26)
internal class LocalCutIntermediateFrameWriter : FrameWriter {
    private val outputQueue = ArrayBlockingQueue<AsyncFrame>(1)
    private val lock = Any()
    private var configuredFormat: Format? = null
    private var configuredUsage: Long = 0L
    private var outstandingBuffer: HardwareBuffer? = null
    private var closed = false
    private var wakeupExecutor: Executor? = null
    private var wakeupListener: Runnable? = null

    private val writerInfo = FrameWriter.Info { format, usage ->
        format.width > 0 &&
            format.height > 0 &&
            usage and REQUIRED_USAGE == REQUIRED_USAGE
    }

    override fun getInfo(): FrameWriter.Info = writerInfo

    override fun configure(format: Format, usage: Long) {
        require(writerInfo.isSupported(format, usage)) {
            "LOCALCUT_INTERMEDIATE_FRAME_FORMAT_UNSUPPORTED"
        }
        synchronized(lock) {
            check(!closed) { "LOCALCUT_INTERMEDIATE_FRAME_WRITER_CLOSED" }
            check(outstandingBuffer == null && outputQueue.isEmpty()) {
                "LOCALCUT_INTERMEDIATE_FRAME_RECONFIGURE_WHILE_BUSY"
            }
            configuredFormat = format
            configuredUsage = usage
        }
    }

    override fun dequeueInputFrame(
        wakeupExecutor: Executor,
        wakeupListener: Runnable,
    ): AsyncFrame? {
        synchronized(lock) {
            check(!closed) { "LOCALCUT_INTERMEDIATE_FRAME_WRITER_CLOSED" }
            val format = checkNotNull(configuredFormat) {
                "LOCALCUT_INTERMEDIATE_FRAME_WRITER_NOT_CONFIGURED"
            }
            if (outstandingBuffer != null || outputQueue.isNotEmpty()) {
                this.wakeupExecutor = wakeupExecutor
                this.wakeupListener = wakeupListener
                return null
            }
            val buffer = HardwareBuffer.create(
                format.width,
                format.height,
                HardwareBuffer.RGBA_8888,
                1,
                configuredUsage,
            )
            outstandingBuffer = buffer
            return AsyncFrame(
                LocalCutHardwareBufferFrame(
                    hardwareBuffer = buffer,
                    format = format,
                ),
                null,
            )
        }
    }

    override fun queueInputFrame(frame: Frame, writeCompleteFence: SyncFenceWrapper?) {
        val hardwareFrame = frame as? HardwareBufferFrame
            ?: throw IllegalArgumentException("LOCALCUT_INTERMEDIATE_FRAME_MUST_USE_HARDWARE_BUFFER")
        synchronized(lock) {
            check(!closed) { "LOCALCUT_INTERMEDIATE_FRAME_WRITER_CLOSED" }
            check(hardwareFrame.hardwareBuffer === outstandingBuffer) {
                "LOCALCUT_INTERMEDIATE_FRAME_NOT_DEQUEUED_HERE"
            }
            check(outputQueue.offer(AsyncFrame(frame, writeCompleteFence))) {
                "LOCALCUT_INTERMEDIATE_FRAME_QUEUE_FULL"
            }
        }
    }

    override fun signalEndOfStream() {
        // Intermediate stages are batch-driven. EOS is owned by the outer FrameProcessor and the
        // final FrameWriter, so no extra sentinel is needed here.
    }

    fun takeOutput(timeoutMs: Long = OUTPUT_TIMEOUT_MS): AsyncFrame {
        val frame = outputQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
            ?: throw IllegalStateException("LOCALCUT_INTERMEDIATE_FRAME_TIMEOUT")
        val wakeup: Pair<Executor, Runnable>?
        synchronized(lock) {
            outstandingBuffer = null
            wakeup = wakeupExecutor?.let { executor ->
                wakeupListener?.let { listener -> executor to listener }
            }
            wakeupExecutor = null
            wakeupListener = null
        }
        wakeup?.first?.execute(wakeup.second)
        return frame
    }

    override fun close() {
        val buffers = LinkedHashSet<HardwareBuffer>()
        synchronized(lock) {
            if (closed) return
            closed = true
            outstandingBuffer?.let(buffers::add)
            outstandingBuffer = null
            configuredFormat = null
            wakeupExecutor = null
            wakeupListener = null
        }
        while (true) {
            val queued = outputQueue.poll() ?: break
            queued.acquireFence?.close()
            (queued.frame as? HardwareBufferFrame)?.hardwareBuffer?.let(buffers::add)
        }
        buffers.forEach { runCatching { it.close() } }
    }

    private companion object {
        const val OUTPUT_TIMEOUT_MS = 5_000L
        const val REQUIRED_USAGE = Frame.USAGE_GPU_COLOR_OUTPUT or Frame.USAGE_GPU_SAMPLED_IMAGE
    }
}

@RequiresApi(26)
internal class LocalCutHardwareBufferFrame(
    private val hardwareBuffer: HardwareBuffer,
    private val format: Format,
    private val metadata: ImmutableMap<String, Any> = ImmutableMap.of(),
    private val contentTimeUs: Long = C.TIME_UNSET,
) : HardwareBufferFrame {
    override fun getHardwareBuffer(): HardwareBuffer = hardwareBuffer

    override fun getFormat(): Format = format

    override fun getMetadata(): ImmutableMap<String, Any> = metadata

    override fun getContentTimeUs(): Long = contentTimeUs

    override fun buildUpon(): HardwareBufferFrame.Builder = Builder(this)

    private class Builder(frame: LocalCutHardwareBufferFrame) : HardwareBufferFrame.Builder {
        private val hardwareBuffer = frame.hardwareBuffer
        private val format = frame.format
        private var metadata: ImmutableMap<String, Any> = frame.metadata
        private var contentTimeUs: Long = frame.contentTimeUs

        override fun setMetadata(metadata: Map<String, Any>): HardwareBufferFrame.Builder = apply {
            this.metadata = ImmutableMap.copyOf(metadata)
        }

        override fun setContentTimeUs(contentTimeUs: Long): HardwareBufferFrame.Builder = apply {
            this.contentTimeUs = contentTimeUs
        }

        override fun build(): HardwareBufferFrame = LocalCutHardwareBufferFrame(
            hardwareBuffer = hardwareBuffer,
            format = format,
            metadata = metadata,
            contentTimeUs = contentTimeUs,
        )
    }
}
