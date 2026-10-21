package com.localcut.editor.media

data class RenderOutputSettings(
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val previewMaxLongEdge: Int? = null,
) {
    init {
        require((width == null) == (height == null)) { "width and height must be provided together" }
        require(width == null || width > 0) { "width must be > 0" }
        require(height == null || height > 0) { "height must be > 0" }
        require(fps == null || fps > 0) { "fps must be > 0" }
        require(previewMaxLongEdge == null || previewMaxLongEdge > 0) { "previewMaxLongEdge must be > 0" }
    }

    companion object {
        fun forExport(profile: ExportProfile): RenderOutputSettings = RenderOutputSettings(
            width = profile.width,
            height = profile.height,
            fps = profile.fps,
        )

        fun forPreview(tier: PreviewQualityTier): RenderOutputSettings = RenderOutputSettings(
            previewMaxLongEdge = tier.maxLongEdge,
        )
    }
}
