package com.localcut.editor.media

import com.localcut.editor.domain.model.TrackCollisionPolicy

object Media3SequenceOpacityResolver {
    fun alphaAt(sequence: Media3SequenceBlueprint, presentationTimeUs: Long): Float {
        val inactiveAlpha = inactiveAlpha(sequence)
        if (presentationTimeUs < 0L) return inactiveAlpha
        var presentationStartUs = 0L
        sequence.items.forEach { item ->
            val endUs = try {
                Math.addExact(presentationStartUs, item.presentationDurationUs)
            } catch (_: ArithmeticException) {
                Long.MAX_VALUE
            }
            if (presentationTimeUs >= presentationStartUs && presentationTimeUs < endUs) {
                return when (item) {
                    is Media3BlueprintItem.Gap -> inactiveAlpha
                    is Media3BlueprintItem.Media -> {
                        val value = PropertyTransformRenderResolver.valueAt(
                            transform = item.transform,
                            keyframes = item.keyframes,
                            propertyAnimations = item.propertyAnimations,
                            presentationTimeUs = presentationTimeUs,
                            presentationStartUs = presentationStartUs,
                        )
                        KeyframeRenderResolver.effectiveOpacity(item.opacity, value).toFloat()
                    }
                }
            }
            presentationStartUs = endUs
        }
        return inactiveAlpha
    }

    fun requiresCustomAlpha(sequences: List<Media3SequenceBlueprint>): Boolean =
        sequences.any { sequence ->
            sequence.sourceCollisionPolicy == TrackCollisionPolicy.OVERLAY ||
                sequence.items.any { item ->
                    item is Media3BlueprintItem.Media && (
                        item.opacity != 1.0 ||
                            item.transform.opacity != 1.0 ||
                            item.keyframes.any { it.value.opacity != 1.0 } ||
                            item.propertyAnimations.any { animation ->
                                animation.propertyId == "transform.opacity" && animation.keyframes.isNotEmpty()
                            }
                        )
                }
        }

    private fun inactiveAlpha(sequence: Media3SequenceBlueprint): Float =
        if (sequence.sourceCollisionPolicy == TrackCollisionPolicy.OVERLAY) 0f else 1f
}
