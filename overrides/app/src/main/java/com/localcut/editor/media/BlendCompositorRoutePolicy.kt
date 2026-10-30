package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode

enum class BlendCompositorRoute {
    DEFAULT_MEDIA3,
    LOCALCUT_MULTI_TEXTURE,
}

object BlendCompositorRoutePolicy {
    fun routeFor(sequences: List<Media3SequenceBlueprint>): BlendCompositorRoute =
        if (requiresLocalCutCompositor(sequences)) {
            BlendCompositorRoute.LOCALCUT_MULTI_TEXTURE
        } else {
            BlendCompositorRoute.DEFAULT_MEDIA3
        }

    private fun requiresLocalCutCompositor(sequences: List<Media3SequenceBlueprint>): Boolean =
        sequences.asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<Media3BlueprintItem.Media>()
            .any { item ->
                item.compositing.blendMode != BlendMode.NORMAL ||
                    item.compositing.chromaKey.enabled ||
                    item.compositing.masks.isNotEmpty()
            }
}
