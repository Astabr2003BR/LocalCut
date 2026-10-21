package com.localcut.editor.media

object Media3SequenceOpacityResolver {
    fun alphaAt(sequence: Media3SequenceBlueprint, presentationTimeUs: Long): Float {
        if (presentationTimeUs < 0L) return 1f
        var presentationStartUs = 0L
        sequence.items.forEach { item ->
            val endUs = try {
                Math.addExact(presentationStartUs, item.presentationDurationUs)
            } catch (_: ArithmeticException) {
                Long.MAX_VALUE
            }
            if (presentationTimeUs >= presentationStartUs && presentationTimeUs < endUs) {
                return when (item) {
                    is Media3BlueprintItem.Gap -> 1f
                    is Media3BlueprintItem.Media -> {
                        val value = KeyframeRenderResolver.valueAt(
                            transform = item.transform,
                            keyframes = item.keyframes,
                            presentationTimeUs = presentationTimeUs,
                            presentationStartUs = presentationStartUs,
                        )
                        KeyframeRenderResolver.effectiveOpacity(item.opacity, value).toFloat()
                    }
                }
            }
            presentationStartUs = endUs
        }
        return 1f
    }

    fun requiresCustomAlpha(sequences: List<Media3SequenceBlueprint>): Boolean =
        sequences.any { sequence ->
            sequence.items.any { item ->
                item is Media3BlueprintItem.Media && (
                    item.opacity != 1.0 ||
                        item.transform.opacity != 1.0 ||
                        item.keyframes.any { it.value.opacity != 1.0 }
                    )
            }
        }
}
