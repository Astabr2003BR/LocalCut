package com.localcut.editor.media

import androidx.media3.common.GlTextureInfo
import java.util.ArrayDeque

internal interface BlendTextureAllocator {
    fun allocate(width: Int, height: Int): GlTextureInfo
    fun release(texture: GlTextureInfo)
}

internal class BlendTextureResources(
    private val allocator: BlendTextureAllocator,
    private val outputCapacity: Int,
) {
    private val freeOutputs = ArrayDeque<GlTextureInfo>()
    private val outputTextureIds = linkedSetOf<Int>()
    private var scratchATexture: GlTextureInfo? = null
    private var scratchBTexture: GlTextureInfo? = null
    private var configuredWidth = 0
    private var configuredHeight = 0
    private var configuredInputCount = 0

    init {
        require(outputCapacity >= 1) { "BLEND_OUTPUT_CAPACITY_INVALID" }
    }

    fun ensureConfigured(
        width: Int,
        height: Int,
        inputCount: Int,
        hasOutputInUse: Boolean,
    ) {
        require(width > 0 && height > 0) { "BLEND_OUTPUT_SIZE_INVALID" }
        require(inputCount >= 2) { "BLEND_COMPOSITOR_REQUIRES_MULTIPLE_INPUTS" }
        if (
            width == configuredWidth &&
            height == configuredHeight &&
            inputCount == configuredInputCount
        ) {
            return
        }
        check(!hasOutputInUse) { "BLEND_OUTPUT_RECONFIGURE_WHILE_IN_USE" }

        releaseAll()
        repeat(outputCapacity) {
            allocator.allocate(width, height).also { texture ->
                outputTextureIds += texture.texId
                freeOutputs.addLast(texture)
            }
        }
        if (inputCount >= 3) scratchATexture = allocator.allocate(width, height)
        if (inputCount >= 4) scratchBTexture = allocator.allocate(width, height)
        configuredWidth = width
        configuredHeight = height
        configuredInputCount = inputCount
    }

    fun acquireOutput(): GlTextureInfo? = freeOutputs.pollFirst()

    fun returnOutput(texture: GlTextureInfo) {
        require(texture.texId in outputTextureIds) { "BLEND_OUTPUT_TEXTURE_FOREIGN" }
        require(freeOutputs.none { it.texId == texture.texId }) { "BLEND_OUTPUT_TEXTURE_ALREADY_FREE" }
        freeOutputs.addLast(texture)
    }

    fun scratchA(): GlTextureInfo? = scratchATexture

    fun scratchB(): GlTextureInfo? = scratchBTexture

    fun releaseAll() {
        val textures = linkedMapOf<Int, GlTextureInfo>()
        freeOutputs.forEach { textures[it.texId] = it }
        scratchATexture?.let { textures[it.texId] = it }
        scratchBTexture?.let { textures[it.texId] = it }
        textures.values.forEach(allocator::release)
        freeOutputs.clear()
        outputTextureIds.clear()
        scratchATexture = null
        scratchBTexture = null
        configuredWidth = 0
        configuredHeight = 0
        configuredInputCount = 0
    }
}
