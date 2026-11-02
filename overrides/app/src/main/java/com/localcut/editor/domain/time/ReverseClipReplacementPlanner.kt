package com.localcut.editor.domain.time

import com.localcut.editor.analysis.ClipAnalysisState
import com.localcut.editor.domain.model.Clip

object ReverseClipReplacementPlanner {
    fun replace(
        clip: Clip,
        replacementAssetId: String,
        originalSourceDurationUs: Long,
        replacementSourceDurationUs: Long,
    ): Clip {
        require(replacementAssetId.isNotBlank()) { "REVERSE_REPLACEMENT_ASSET_ID_REQUIRED" }
        require(originalSourceDurationUs > 0L) { "REVERSE_ORIGINAL_DURATION_INVALID" }
        require(replacementSourceDurationUs > 0L) { "REVERSE_REPLACEMENT_DURATION_INVALID" }
        require(clip.sourceInUs >= 0L && clip.sourceOutUs > clip.sourceInUs) {
            "REVERSE_CLIP_SOURCE_RANGE_INVALID"
        }
        require(clip.sourceOutUs <= originalSourceDurationUs) { "REVERSE_CLIP_SOURCE_RANGE_OUT_OF_BOUNDS" }
        require(!clip.timeMap.reverse) { "REVERSE_CLIP_ALREADY_REVERSED" }
        require(clip.timeMap.freezeSegments.isEmpty()) { "REVERSE_CLIP_FREEZE_UNSUPPORTED" }

        val replacementSourceInUs = originalSourceDurationUs - clip.sourceOutUs
        val replacementSourceOutUs = originalSourceDurationUs - clip.sourceInUs
        require(replacementSourceInUs >= 0L && replacementSourceOutUs > replacementSourceInUs) {
            "REVERSE_REPLACEMENT_SOURCE_RANGE_INVALID"
        }
        require(replacementSourceOutUs <= replacementSourceDurationUs) {
            "REVERSE_REPLACEMENT_SOURCE_RANGE_OUT_OF_BOUNDS"
        }

        return clip.copy(
            assetId = replacementAssetId,
            sourceInUs = replacementSourceInUs,
            sourceOutUs = replacementSourceOutUs,
            analysis = ClipAnalysisState(),
        )
    }
}
