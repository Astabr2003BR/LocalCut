package com.localcut.editor.media

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

@UnstableApi
internal class LocalCutMotionBlurEffect(
    private val shutterAngle: Double,
    private val requestedSamples: Int,
) : GlEffect {
    init {
        require(shutterAngle.isFinite() && shutterAngle in 0.0..360.0) {
            "motion blur shutter angle must be finite and in 0..360"
        }
        require(requestedSamples in 1..MotionBlurPolicy.MAX_REQUESTED_SAMPLES) {
            "motion blur samples must be in 1..${MotionBlurPolicy.MAX_REQUESTED_SAMPLES}"
        }
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        shutterAngle == 0.0 || requestedSamples == 1

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        LocalCutMotionBlurShaderProgram(
            shutterAngle = shutterAngle,
            requestedSamples = requestedSamples,
            useHdr = useHdr,
        )
}

@UnstableApi
private class LocalCutMotionBlurShaderProgram(
    private val shutterAngle: Double,
    private val requestedSamples: Int,
    private val useHdr: Boolean,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ useHdr,
    /* texturePoolCapacity = */ 1,
) {
    private val glProgram: GlProgram
    private var width = 0
    private var height = 0
    private var historyTextureIds = IntArray(0)
    private var historyFramebufferIds = IntArray(0)
    private var historyRing = MotionBlurHistoryRing(0)
    private var lastPresentationTimeUs: Long? = null

    init {
        try {
            glProgram = GlProgram(
                LocalCutMotionBlurShaderContract.vertexShader,
                LocalCutMotionBlurShaderContract.fragmentShader,
            )
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        require(inputWidth > 0 && inputHeight > 0) { "motion blur input dimensions must be positive" }
        releaseHistory()
        width = inputWidth
        height = inputHeight
        lastPresentationTimeUs = null

        val effectiveSamples = MotionBlurPolicy.effectiveSamples(
            requestedSamples = requestedSamples,
            width = inputWidth,
            height = inputHeight,
            bytesPerPixel = if (useHdr) HDR_BYTES_PER_PIXEL else SDR_BYTES_PER_PIXEL,
        )
        allocateHistory(effectiveSamples - 1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (MotionBlurPolicy.shouldResetHistory(lastPresentationTimeUs, presentationTimeUs)) {
                historyRing.reset()
            }

            val availableSamples = 1 + historyRing.size
            drawTemporalPass(
                inputTexId = inputTexId,
                availableSamples = availableSamples,
                weights = MotionBlurPolicy.weights(shutterAngle, availableSamples),
            )
            copyCurrentFrameIntoHistory(inputTexId)
            lastPresentationTimeUs = presentationTimeUs
        } catch (error: VideoFrameProcessingException) {
            throw error
        } catch (error: Throwable) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        var failure: Throwable? = null
        try {
            releaseHistory()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            glProgram.delete()
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        try {
            super.release()
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw VideoFrameProcessingException(it) }
    }

    private fun allocateHistory(capacity: Int) {
        require(capacity in 0 until LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES) {
            "motion blur history capacity exceeds shader contract"
        }
        historyRing = MotionBlurHistoryRing(capacity)
        if (capacity == 0) {
            historyTextureIds = IntArray(0)
            historyFramebufferIds = IntArray(0)
            return
        }

        val previousFramebuffer = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFramebuffer, 0)
        val textures = IntArray(capacity)
        val framebuffers = IntArray(capacity)
        try {
            repeat(capacity) { index ->
                val textureId = GlUtil.createTexture(width, height, useHdr)
                textures[index] = textureId
                GLES20.glGenFramebuffers(1, framebuffers, index)
                checkGl("motion blur framebuffer allocation")
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[index])
                GLES20.glFramebufferTexture2D(
                    GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D,
                    textureId,
                    0,
                )
                val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) {
                    "motion blur framebuffer incomplete: 0x${status.toString(16)}"
                }
            }
            historyTextureIds = textures
            historyFramebufferIds = framebuffers
        } catch (error: Throwable) {
            releaseAllocated(textures, framebuffers)
            historyRing = MotionBlurHistoryRing(0)
            throw VideoFrameProcessingException(error)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFramebuffer[0])
        }
    }

    private fun drawTemporalPass(
        inputTexId: Int,
        availableSamples: Int,
        weights: List<Float>,
    ) {
        glProgram.use()
        bindTemporalUniforms(inputTexId, availableSamples, weights)
        glProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGl("motion blur temporal draw")
    }

    private fun bindTemporalUniforms(
        inputTexId: Int,
        availableSamples: Int,
        weights: List<Float>,
    ) {
        require(availableSamples in 1..LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES)
        require(weights.size == availableSamples)

        glProgram.setSamplerTexIdUniform("uCurrentSampler", inputTexId, 0)
        val newestFirst = historyRing.newestFirstIndices()
        repeat(LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES - 1) { age ->
            val historyIndex = newestFirst.getOrNull(age)
            val textureId = historyIndex?.let(historyTextureIds::get) ?: inputTexId
            glProgram.setSamplerTexIdUniform("uHistory$age", textureId, age + 1)
        }
        setWeights(weights, availableSamples)
    }

    private fun bindCopyUniforms(inputTexId: Int) {
        glProgram.setSamplerTexIdUniform("uCurrentSampler", inputTexId, 0)
        repeat(LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES - 1) { age ->
            // Never bind an owned history texture while that texture may be the active FBO target.
            glProgram.setSamplerTexIdUniform("uHistory$age", inputTexId, age + 1)
        }
        setWeights(LocalCutMotionBlurShaderContract.currentFrameOnlyWeights().take(1), 1)
    }

    private fun setWeights(weights: List<Float>, availableSamples: Int) {
        repeat(LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES) { index ->
            glProgram.setFloatUniform("uWeight$index", weights.getOrElse(index) { 0f })
        }
        glProgram.setFloatUniform("uSampleCount", availableSamples.toFloat())
    }

    private fun copyCurrentFrameIntoHistory(inputTexId: Int) {
        val writeIndex = historyRing.nextWriteIndex() ?: return

        val previousFramebuffer = IntArray(1)
        val previousViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFramebuffer, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0)
        try {
            GLES20.glBindFramebuffer(
                GLES20.GL_FRAMEBUFFER,
                historyFramebufferIds[writeIndex],
            )
            GLES20.glViewport(0, 0, width, height)
            glProgram.use()
            bindCopyUniforms(inputTexId)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            checkGl("motion blur history copy")
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFramebuffer[0])
            GLES20.glViewport(
                previousViewport[0],
                previousViewport[1],
                previousViewport[2],
                previousViewport[3],
            )
        }
        historyRing.commitWrite()
    }

    private fun releaseHistory() {
        var failure: Throwable? = null
        try {
            if (historyFramebufferIds.isNotEmpty()) {
                GLES20.glDeleteFramebuffers(
                    historyFramebufferIds.size,
                    historyFramebufferIds,
                    0,
                )
                checkGl("motion blur framebuffer release")
            }
        } catch (error: Throwable) {
            failure = error
        }

        historyTextureIds.forEach { textureId ->
            try {
                GlUtil.deleteTexture(textureId)
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }

        historyTextureIds = IntArray(0)
        historyFramebufferIds = IntArray(0)
        historyRing = MotionBlurHistoryRing(0)
        lastPresentationTimeUs = null
        failure?.let { throw it }
    }

    private fun releaseAllocated(
        textures: IntArray,
        framebuffers: IntArray,
    ) {
        runCatching {
            if (framebuffers.isNotEmpty()) {
                GLES20.glDeleteFramebuffers(framebuffers.size, framebuffers, 0)
            }
        }
        textures.filter { it != 0 }.forEach { textureId ->
            runCatching { GlUtil.deleteTexture(textureId) }
        }
    }

    private fun checkGl(operation: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) {
            "$operation failed with GL error 0x${error.toString(16)}"
        }
    }

    private companion object {
        const val SDR_BYTES_PER_PIXEL = 4
        const val HDR_BYTES_PER_PIXEL = 8
    }
}
