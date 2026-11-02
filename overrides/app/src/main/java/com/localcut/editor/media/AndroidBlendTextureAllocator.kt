package com.localcut.editor.media

import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal class AndroidBlendTextureAllocator(
    private val glObjectsProvider: GlObjectsProvider,
) : BlendTextureAllocator {
    override fun allocate(width: Int, height: Int): GlTextureInfo {
        require(width > 0 && height > 0) { "BLEND_TEXTURE_SIZE_INVALID" }
        val texId = GlUtil.createTexture(width, height, false)
        return try {
            glObjectsProvider.createBuffersForTexture(texId, width, height)
        } catch (error: Throwable) {
            runCatching { GlUtil.deleteTexture(texId) }
            throw error
        }
    }

    override fun release(texture: GlTextureInfo) {
        texture.release()
    }
}
