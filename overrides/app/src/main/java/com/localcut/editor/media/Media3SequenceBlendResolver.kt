package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode

/**
 * Resolves the blend contract for a visual sequence without enabling a rendering backend.
 *
 * Non-NORMAL modes require access to both the source texture and the already-composited
 * backdrop. The current Media3 overlay settings path only exposes alpha/layout, so callers
 * must keep those modes blocked until a real multi-texture compositor is installed.
 */
object Media3SequenceBlendResolver {
    fun blendModeAt(
        sequence: Media3SequenceBlueprint,
        presentationTimeUs: Long,
    ): BlendMode {
        if (presentationTimeUs < 0L) return BlendMode.NORMAL

        var presentationStartUs = 0L
        sequence.items.forEach { item ->
            val endUs = try {
                Math.addExact(presentationStartUs, item.presentationDurationUs)
            } catch (_: ArithmeticException) {
                Long.MAX_VALUE
            }
            if (presentationTimeUs >= presentationStartUs && presentationTimeUs < endUs) {
                return when (item) {
                    is Media3BlueprintItem.Gap -> BlendMode.NORMAL
                    is Media3BlueprintItem.Media -> item.compositing.blendMode
                }
            }
            presentationStartUs = endUs
        }
        return BlendMode.NORMAL
    }

    fun requiresPixelCompositor(sequences: List<Media3SequenceBlueprint>): Boolean =
        sequences.any { sequence ->
            sequence.items.any { item ->
                item is Media3BlueprintItem.Media && item.compositing.blendMode != BlendMode.NORMAL
            }
        }

    fun nonNormalModes(sequences: List<Media3SequenceBlueprint>): Set<BlendMode> =
        buildSet {
            sequences.forEach { sequence ->
                sequence.items.forEach { item ->
                    if (item is Media3BlueprintItem.Media && item.compositing.blendMode != BlendMode.NORMAL) {
                        add(item.compositing.blendMode)
                    }
                }
            }
        }
}
