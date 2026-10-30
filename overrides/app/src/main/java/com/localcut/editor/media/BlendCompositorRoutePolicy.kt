package com.localcut.editor.media

enum class BlendCompositorRoute {
    DEFAULT_MEDIA3,
    LOCALCUT_MULTI_TEXTURE,
}

object BlendCompositorRoutePolicy {
    fun routeFor(sequences: List<Media3SequenceBlueprint>): BlendCompositorRoute =
        if (Media3SequenceBlendModeResolver.requiresCustomBlend(sequences)) {
            BlendCompositorRoute.LOCALCUT_MULTI_TEXTURE
        } else {
            BlendCompositorRoute.DEFAULT_MEDIA3
        }
}
