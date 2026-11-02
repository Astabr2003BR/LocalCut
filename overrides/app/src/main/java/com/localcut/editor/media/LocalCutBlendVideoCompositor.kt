package com.localcut.editor.media

import android.opengl.EGL14
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import androidx.annotation.OptIn
import androidx.media3.common.ColorInfo
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlTextureProducer
import androidx.media3.effect.VideoCompositor
import com.localcut.editor.domain.model.BlendMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
internal class LocalCutBlendVideoCompositor(
    private val glObjectsProvider: GlObjectsProvider,
    private val executor: ExecutorService,
    private val listener: VideoCompositor.Listener,
    private val textureOutputListener: GlTextureProducer.Listener,
    outputCapacity: Int = 1,
) : VideoCompositor {
    private data class FramePayload(
        val producer: GlTextureProducer,
        val texture: GlTextureInfo,
        val alphaScale: Float,
        val mode: BlendMode,
    )

    private data class OutputRecord(
        val texture: GlTextureInfo,
        val syncObject: Long,
    )

    private val lock = Any()
    private val timeline = BlendInputTimeline<FramePayload>()
    private val leasePolicy = BlendOutputLeasePolicy(outputCapacity)
    private val textureAllocator = AndroidBlendTextureAllocator(glObjectsProvider)
    private val textureResources = BlendTextureResources(textureAllocator, outputCapacity)
    private val outputRecords = linkedMapOf<Long, OutputRecord>()
    private val released = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)

    @Volatile private var settings: VideoCompositorSettings = VideoCompositorSettings.DEFAULT
    private var configuredColorInfo: ColorInfo? = null
    private var eglDisplay: EGLDisplay? = null
    private var placeholderSurface: EGLSurface? = null
    private var glThread: Thread? = null
    private var frameComposer: LocalCutBlendFrameComposer? = null

    init {
        submitGl(::setupGl)
    }

    override fun registerInputSource(inputIndex: Int) {
        checkUsable()
        synchronized(lock) { timeline.registerInput(inputIndex) }
    }

    override fun setVideoCompositorSettings(videoCompositorSettings: VideoCompositorSettings) {
        checkUsable()
        settings = videoCompositorSettings
    }

    override fun signalEndOfInputSource(inputIndex: Int) {
        checkUsable()
        val toRelease = synchronized(lock) {
            timeline.endInput(inputIndex)
            if (timeline.allEndedAndPrimaryEmpty()) timeline.consumePrimaryAndPrune() else emptyList()
        }
        releaseInputFrames(toRelease)
        if (synchronized(lock) { timeline.allEndedAndPrimaryEmpty() }) {
            listener.onEnded()
        } else {
            submitGl(::maybeComposite)
        }
    }

    override fun queueInputTexture(
        inputIndex: Int,
        textureProducer: GlTextureProducer,
        inputTexture: GlTextureInfo,
        colorInfo: ColorInfo,
        presentationTimeUs: Long,
    ) {
        checkUsable()
        require(!ColorInfo.isTransferHdr(colorInfo)) { "BLEND_HDR_UNSUPPORTED" }
        synchronized(lock) {
            val current = configuredColorInfo
            if (current == null) configuredColorInfo = colorInfo
            else require(current == colorInfo) { "BLEND_MIXED_COLOR_INFO_UNSUPPORTED" }
        }

        val currentSettings = settings
        val alphaScale = LocalCutBlendShaderContract.validateAlphaScale(
            currentSettings.getOverlaySettings(inputIndex, presentationTimeUs).alphaScale,
        )
        val mode = (currentSettings as? KeyframeVideoCompositorSettings)
            ?.blendModeAt(inputIndex, presentationTimeUs)
            ?: BlendMode.NORMAL
        val toRelease = synchronized(lock) {
            timeline.queue(
                inputId = inputIndex,
                presentationTimeUs = presentationTimeUs,
                payload = FramePayload(textureProducer, inputTexture, alphaScale, mode),
            )
        }
        releaseInputFrames(toRelease)
        submitGl(::maybeComposite)
    }

    override fun releaseOutputTexture(presentationTimeUs: Long) {
        if (released.get()) return
        submitGl { releaseOutputThrough(presentationTimeUs) }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        val cleanup = { runCatching(::releaseGl).onFailure(::notifyError) }
        if (Thread.currentThread() === glThread) {
            cleanup()
            return
        }
        runCatching { executor.submit(cleanup).get(1, TimeUnit.SECONDS) }
            .onFailure(::notifyError)
    }

    private fun setupGl() {
        glThread = Thread.currentThread()
        val display = GlUtil.getDefaultEglDisplay()
        val context = glObjectsProvider.createEglContext(
            display,
            2,
            GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888,
        )
        placeholderSurface = glObjectsProvider.createFocusedPlaceholderEglSurface(context, display)
        eglDisplay = display
        frameComposer = LocalCutBlendFrameComposer()
    }

    private fun maybeComposite() {
        if (released.get() || failed.get() || !leasePolicy.canAcquire()) return
        val selected = synchronized(lock) { timeline.selectForNextOutput() } ?: return
        val textures = selected.map { it.payload.texture }
        val outputSize = settings.getOutputSize(textures.map { Size(it.width, it.height) })
        textureResources.ensureConfigured(
            width = outputSize.width,
            height = outputSize.height,
            inputCount = selected.size,
            hasOutputInUse = !leasePolicy.isEmpty(),
        )
        val output = textureResources.acquireOutput() ?: return
        val timestampUs = selected.first().presentationTimeUs

        try {
            frameComposer!!.compose(
                inputs = selected.mapIndexed { inputId, frame ->
                    BlendGpuInput(
                        inputId = inputId,
                        texture = frame.payload.texture,
                        alphaScale = frame.payload.alphaScale,
                        mode = frame.payload.mode,
                    )
                },
                output = output,
                scratchA = textureResources.scratchA(),
                scratchB = textureResources.scratchB(),
            )
            val syncObject = GlUtil.createGlSyncFence()
            leasePolicy.onAcquired(timestampUs)
            outputRecords[timestampUs] = OutputRecord(output, syncObject)
            textureOutputListener.onTextureRendered(this, output, timestampUs, syncObject)
        } catch (error: Throwable) {
            if (!outputRecords.containsKey(timestampUs)) textureResources.returnOutput(output)
            throw error
        }

        val consumed = synchronized(lock) { timeline.consumePrimaryAndPrune() }
        releaseInputFrames(consumed)
        if (synchronized(lock) { timeline.allEndedAndPrimaryEmpty() }) listener.onEnded()
    }

    private fun releaseOutputThrough(presentationTimeUs: Long) {
        leasePolicy.releaseThrough(presentationTimeUs).forEach { timestampUs ->
            val record = outputRecords.remove(timestampUs) ?: error("BLEND_OUTPUT_RECORD_MISSING")
            GlUtil.deleteSyncObject(record.syncObject)
            textureResources.returnOutput(record.texture)
        }
        maybeComposite()
    }

    private fun releaseInputFrames(frames: List<BlendTimelineFrame<FramePayload>>) {
        frames.forEach { frame -> frame.payload.producer.releaseOutputTexture(frame.presentationTimeUs) }
    }

    private fun releaseGl() {
        val queued = synchronized(lock) { timeline.drainAll() }
        releaseInputFrames(queued)
        frameComposer?.release()
        frameComposer = null

        outputRecords.values.forEach { record ->
            runCatching { GlUtil.deleteSyncObject(record.syncObject) }
            runCatching { textureAllocator.release(record.texture) }
        }
        outputRecords.clear()
        textureResources.releaseAll()

        val display = eglDisplay
        val surface = placeholderSurface
        if (display != null && surface != null && surface != EGL14.EGL_NO_SURFACE) {
            GlUtil.destroyEglSurface(display, surface)
        }
        placeholderSurface = null
        eglDisplay = null
    }

    private fun submitGl(task: () -> Unit) {
        if (released.get() || failed.get()) return
        executor.execute {
            if (released.get() || failed.get()) return@execute
            try {
                task()
            } catch (error: Throwable) {
                notifyError(error)
            }
        }
    }

    private fun checkUsable() {
        check(!released.get()) { "BLEND_COMPOSITOR_RELEASED" }
        check(!failed.get()) { "BLEND_COMPOSITOR_FAILED" }
    }

    private fun notifyError(error: Throwable) {
        if (!failed.compareAndSet(false, true)) return
        listener.onError(
            if (error is VideoFrameProcessingException) error else VideoFrameProcessingException(error),
        )
    }
}
