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
    private var historyCount = 0
    private var nextHistoryIndex = 0
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
        historyCount = 0
        nextHistoryIndex = 0
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
                historyCount = 0
                nextHistoryIndex = 0
            }

            val availableSamples = 1 + historyCount
            val weights = MotionBlurPolicy.weights(shutterAngle, availableSamples)
            drawTemporalPass(
                inputTexId = inputTexId,
                availableSamples = availableSamples,
                weights = weights,
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
        if (capacity == 0) {
            historyTextureIds = IntArray(0)
            historyFramebufferIds = IntArray(0)
            return
        }

        val previousFramebuffer = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFramebuffer, 0)
        val textures = IntArray(capacity)
        val framebuffers = IntArray(capacity)
        var allocated = 0
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
                allocated += 1
            }
            historyTextureIds = textures
            historyFramebufferIds = framebuffers
        } catch (error: Throwable) {
            releaseAllocated(textures, framebuffers, allocated)
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
        repeat(LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES - 1) { age ->
            val textureId = historyTextureForAge(age) ?: inputTexId
            glProgram.setSamplerTexIdUniform("uHistory$age", textureId, age + 1)
        }
        repeat(LocalCutMotionBlurShaderContract.MAX_SHADER_SAMPLES) { index ->
            glProgram.setFloatUniform("uWeight$index", weights.getOrElse(index) { 0f })
        }
        glProgram.setFloatUniform("uSampleCount", availableSamples.toFloat())
    }

    private fun copyCurrentFrameIntoHistory(inputTexId: Int) {
        if (historyFramebufferIds.isEmpty()) return

        val previousFramebuffer = IntArray(1)
        val previousViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFramebuffer, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0)
        try {
            GLES20.glBindFramebuffer(
                GLES20.GL_FRAMEBUFFER,
                historyFramebufferIds[nextHistoryIndex],
            )
            GLES20.glViewport(0, 0, width, height)
            glProgram.use()
            bindTemporalUniforms(
                inputTexId = inputTexId,
                availableSamples = 1,
                weights = LocalCutMotionBlurShaderContract.currentFrameOnlyWeights().take(1),
            )
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

        nextHistoryIndex = (nextHistoryIndex + 1) % historyFramebufferIds.size
        historyCount = minOf(historyCount + 1, historyFramebufferIds.size)
    }

    private fun historyTextureForAge(age: Int): Int? {
        if (age !in 0 until historyCount || historyTextureIds.isEmpty()) return null
        val index = Math.floorMod(nextHistoryIndex - 1 - age, historyTextureIds.size)
        return historyTextureIds[index]
    }

    private fun releaseHistory() {
        if (historyFramebufferIds.isNotEmpty()) {
            GLES20.glDeleteFramebuffers(
                historyFramebufferIds.size,
                historyFramebufferIds,
                0,
            )
            checkGl("motion blur framebuffer release")
        }
        historyTextureIds.forEach(GlUtil::deleteTexture)
        historyTextureIds = IntArray(0)
        historyFramebufferIds = IntArray(0)
        historyCount = 0
        nextHistoryIndex = 0
        lastPresentationTimeUs = null
    }

    private fun releaseAllocated(
        textures: IntArray,
        framebuffers: IntArray,
        allocated: Int,
    ) {
        if (allocated > 0) {
            GLES20.glDeleteFramebuffers(allocated, framebuffers, 0)
        }
        for (index in 0 until allocated) {
            if (textures[index] != 0) runCatching { GlUtil.deleteTexture(textures[index]) }
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
