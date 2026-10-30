package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode

/** Resolves the blend contract of one visual input without enabling unsupported rendering. */
object Media3SequenceBlendModeResolver {
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

    fun requiresCustomBlend(sequences: List<Media3SequenceBlueprint>): Boolean =
        sequences.any { sequence ->
            sequence.items.any { item ->
                item is Media3BlueprintItem.Media && item.compositing.blendMode != BlendMode.NORMAL
            }
        }
}
