@file:OptIn(
    androidx.media3.common.util.ExperimentalApi::class,
    androidx.media3.common.util.UnstableApi::class,
)

package com.localcut.editor.media

import android.hardware.HardwareBuffer
import android.opengl.EGLDisplay
import android.opengl.GLES20
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil
import androidx.media3.common.video.AsyncFrame
import androidx.media3.common.video.Frame
import androidx.media3.common.video.HardwareBufferFrame
import androidx.media3.effect.DefaultGlFrameProcessor
import androidx.media3.effect.HardwareBufferJniWrapper
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap

@RequiresApi(26)
internal class LocalCutHardwareBufferBlendRenderer(
    private val eglDisplay: EGLDisplay,
    private val hardwareBufferJni: HardwareBufferJniWrapper,
) {
    private val composer = LocalCutBlendFrameComposer()

    fun compose(inputs: List<AsyncFrame>): HardwareBufferFrame {
        require(inputs.isNotEmpty()) { "BLEND_INPUTS_EMPTY" }
        val mappedInputs = mutableListOf<MappedInput>()
        var outputBuffer: HardwareBuffer? = null
        var outputImage = 0L
        var outputTexture = 0
        var outputFbo = 0
        var scratchA: GlTextureInfo? = null
        var scratchB: GlTextureInfo? = null
        var transparent: GlTextureInfo? = null

        try {
            val frames = inputs.map { async ->
                async.acquireFence?.let { fence ->
                    try {
                        check(fence.awaitMs(FENCE_TIMEOUT_MS)) { "BLEND_INPUT_FENCE_TIMEOUT" }
                    } finally {
                        fence.close()
                    }
                }
                async.frame as? HardwareBufferFrame
                    ?: throw IllegalArgumentException("BLEND_INPUT_MUST_USE_HARDWARE_BUFFER")
            }
            val first = frames.first()
            val width = first.hardwareBuffer.width
            val height = first.hardwareBuffer.height
            require(width > 0 && height > 0) { "BLEND_FRAME_SIZE_INVALID" }
            frames.forEach { frame ->
                require(frame.hardwareBuffer.format == HardwareBuffer.RGBA_8888) {
                    "BLEND_INPUT_FORMAT_UNSUPPORTED"
                }
                require(frame.hardwareBuffer.width == width && frame.hardwareBuffer.height == height) {
                    "BLEND_TEXTURE_SIZE_MISMATCH"
                }
            }

            val presentationTimeUs = (first.metadata[Frame.KEY_PRESENTATION_TIME_US] as? Long)
                ?: throw IllegalArgumentException("BLEND_PRESENTATION_TIME_MISSING")
            val settings = first.metadata[DefaultGlFrameProcessor.KEY_COMPOSITOR_SETTINGS]
                as? KeyframeVideoCompositorSettings
                ?: throw IllegalArgumentException("BLEND_COMPOSITOR_SETTINGS_MISSING")

            val resolved = frames.map { frame ->
                val sequenceIndex = frame.metadata[DefaultGlFrameProcessor.KEY_COMPOSITION_SEQUENCE_INDEX]
                    as? Int ?: throw IllegalArgumentException("BLEND_SEQUENCE_INDEX_MISSING")
                ResolvedInput(
                    sequenceIndex = sequenceIndex,
                    frame = frame,
                    alphaScale = settings.getOverlaySettings(sequenceIndex, presentationTimeUs).alphaScale,
                    mode = settings.blendModeAt(sequenceIndex, presentationTimeUs),
                )
            }.sortedBy { it.sequenceIndex }
            require(resolved.map { it.sequenceIndex }.distinct().size == resolved.size) {
                "BLEND_SEQUENCE_INDEX_DUPLICATE"
            }

            resolved.forEachIndexed { localInputId, input ->
                val mapped = mapInput(input.frame)
                mappedInputs += mapped
                mapped.gpuInput = BlendGpuInput(
                    inputId = localInputId,
                    texture = mapped.texture,
                    alphaScale = input.alphaScale,
                    mode = input.mode,
                )
            }

            outputBuffer = HardwareBuffer.create(
                width,
                height,
                HardwareBuffer.RGBA_8888,
                1,
                Frame.USAGE_GPU_COLOR_OUTPUT or Frame.USAGE_GPU_SAMPLED_IMAGE,
            )
            outputImage = hardwareBufferJni.nativeCreateEglImageFromHardwareBuffer(
                eglDisplay.nativeHandle,
                outputBuffer,
            )
            check(outputImage != 0L) { "BLEND_OUTPUT_EGL_IMAGE_FAILED" }
            outputTexture = GlUtil.generateTexture()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputTexture)
            check(hardwareBufferJni.nativeBindEGLImage(GLES20.GL_TEXTURE_2D, outputImage)) {
                "BLEND_OUTPUT_EGL_BIND_FAILED"
            }
            outputFbo = GlUtil.createFboForTexture(outputTexture)
            val outputInfo = GlTextureInfo(outputTexture, outputFbo, C.INDEX_UNSET, width, height)

            if (mappedInputs.size == 1) {
                transparent = createTransparentTarget(width, height)
                composer.composeSingle(
                    input = mappedInputs.single().gpuInput,
                    transparentBackdrop = transparent,
                    output = outputInfo,
                )
            } else {
                val passCount = mappedInputs.size - 1
                val targets = BlendRenderTargetPlanner.targetsFor(passCount)
                if (BlendRenderTarget.SCRATCH_A in targets) scratchA = createScratch(width, height)
                if (BlendRenderTarget.SCRATCH_B in targets) scratchB = createScratch(width, height)
                composer.compose(
                    inputs = mappedInputs.map { it.gpuInput },
                    output = outputInfo,
                    scratchA = scratchA,
                    scratchB = scratchB,
                )
            }
            GLES20.glFinish()
            GlUtil.checkGlError()

            val outputMetadata = ImmutableMap.builder<String, Any>()
                .putAll(first.metadata)
                .put(DefaultGlFrameProcessor.KEY_ITEM_EFFECTS, ImmutableList.of<Effect>())
                .buildKeepingLast()
            return LocalCutHardwareBufferFrame(
                hardwareBuffer = outputBuffer,
                format = first.format.buildUpon().setWidth(width).setHeight(height).setRotationDegrees(0).build(),
                metadata = outputMetadata,
                contentTimeUs = first.contentTimeUs,
            ).also {
                outputBuffer = null
            }
        } finally {
            mappedInputs.forEach { mapped -> mapped.release() }
            releaseTexture(scratchA)
            releaseTexture(scratchB)
            releaseTexture(transparent)
            if (outputFbo != 0) runCatching { GlUtil.deleteFbo(outputFbo) }
            if (outputTexture != 0) runCatching { GlUtil.deleteTexture(outputTexture) }
            if (outputImage != 0L) runCatching {
                hardwareBufferJni.nativeDestroyEGLImage(eglDisplay.nativeHandle, outputImage)
            }
            outputBuffer?.close()
        }
    }

    fun release() {
        composer.release()
    }

    private fun mapInput(frame: HardwareBufferFrame): MappedInput {
        val buffer = frame.hardwareBuffer
        val image = hardwareBufferJni.nativeCreateEglImageFromHardwareBuffer(
            eglDisplay.nativeHandle,
            buffer,
        )
        check(image != 0L) { "BLEND_INPUT_EGL_IMAGE_FAILED" }
        var texture = 0
        try {
            texture = GlUtil.generateTexture()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            check(hardwareBufferJni.nativeBindEGLImage(GLES20.GL_TEXTURE_2D, image)) {
                "BLEND_INPUT_EGL_BIND_FAILED"
            }
            return MappedInput(
                texture = GlTextureInfo(
                    texture,
                    C.INDEX_UNSET,
                    C.INDEX_UNSET,
                    buffer.width,
                    buffer.height,
                ),
                imageHandle = image,
                textureId = texture,
            )
        } catch (error: Throwable) {
            if (texture != 0) runCatching { GlUtil.deleteTexture(texture) }
            runCatching { hardwareBufferJni.nativeDestroyEGLImage(eglDisplay.nativeHandle, image) }
            throw error
        }
    }

    private fun createScratch(width: Int, height: Int): GlTextureInfo {
        val texture = GlUtil.createTexture(width, height, false)
        return GlTextureInfo(
            texture,
            GlUtil.createFboForTexture(texture),
            C.INDEX_UNSET,
            width,
            height,
        )
    }

    private fun createTransparentTarget(width: Int, height: Int): GlTextureInfo =
        createScratch(width, height).also { target ->
            GlUtil.focusFramebufferUsingCurrentContext(target.fboId, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GlUtil.checkGlError()
        }

    private fun releaseTexture(texture: GlTextureInfo?) {
        if (texture == null) return
        if (texture.fboId != C.INDEX_UNSET) runCatching { GlUtil.deleteFbo(texture.fboId) }
        if (texture.texId != C.INDEX_UNSET) runCatching { GlUtil.deleteTexture(texture.texId) }
    }

    private inner class MappedInput(
        val texture: GlTextureInfo,
        private val imageHandle: Long,
        private val textureId: Int,
    ) {
        lateinit var gpuInput: BlendGpuInput

        fun release() {
            if (textureId != 0) runCatching { GlUtil.deleteTexture(textureId) }
            if (imageHandle != 0L) runCatching {
                hardwareBufferJni.nativeDestroyEGLImage(eglDisplay.nativeHandle, imageHandle)
            }
        }
    }

    private data class ResolvedInput(
        val sequenceIndex: Int,
        val frame: HardwareBufferFrame,
        val alphaScale: Float,
        val mode: com.localcut.editor.domain.model.BlendMode,
    )

    private companion object {
        const val FENCE_TIMEOUT_MS = 5_000L
    }
}