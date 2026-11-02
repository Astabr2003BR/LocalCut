package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.time.TimeMapEvaluator

internal data class Media3FreezePiece(
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val presentationDurationUs: Long,
    val clipLocalStartUs: Long,
    val outputTracks: Media3OutputTracks,
    val timeMap: TimeMap,
    val isFreezeHold: Boolean,
)

internal object Media3FreezeFramePlanner {
    fun canRender(
        assetType: AssetType,
        timeMap: TimeMap,
        fadeInUs: Long,
        fadeOutUs: Long,
        sourceRangeUs: Long? = null,
        presentationDurationUs: Long? = null,
    ): Boolean {
        if (assetType != AssetType.VIDEO) return false
        if (timeMap.validationIssues().isNotEmpty()) return false
        if (timeMap.freezeSegments.isEmpty()) return false
        if (timeMap.reverse || timeMap.speedPoints.isNotEmpty()) return false
        if (fadeInUs != 0L || fadeOutUs != 0L) return false

        if (sourceRangeUs != null) {
            if (sourceRangeUs <= 0L) return false
            if (timeMap.freezeSegments.any { freeze -> freeze.sourceOffsetUs > sourceRangeUs }) return false
            if (presentationDurationUs != null) {
                if (presentationDurationUs <= 0L) return false
                val expectedDurationUs = runCatching {
                    TimeMapEvaluator.presentationDurationUs(sourceRangeUs, timeMap)
                }.getOrNull() ?: return false
                if (expectedDurationUs != presentationDurationUs) return false
                if (timeMap.freezeSegments.any { freeze ->
                        freeze.durationUs > presentationDurationUs ||
                            freeze.startOffsetUs > presentationDurationUs - freeze.durationUs
                    }
                ) {
                    return false
                }
                if (!movingRangesAdvance(timeMap, sourceRangeUs, presentationDurationUs)) return false
            }
        }
        return true
    }

    fun plan(item: Media3BlueprintItem.Media, fps: Int): List<Media3FreezePiece> {
        require(fps > 0) { "fps must be positive" }
        val sourceRangeUs = Math.subtractExact(item.sourceOutUs, item.sourceInUs)
        require(sourceRangeUs > 0L) { "freeze source range must be positive" }

        if (item.timeMap.freezeSegments.isEmpty()) {
            return listOf(
                Media3FreezePiece(
                    sourceInUs = item.sourceInUs,
                    sourceOutUs = item.sourceOutUs,
                    presentationDurationUs = item.presentationDurationUs,
                    clipLocalStartUs = 0L,
                    outputTracks = item.outputTracks,
                    timeMap = item.timeMap,
                    isFreezeHold = false,
                ),
            )
        }

        require(
            canRender(
                assetType = item.assetType,
                timeMap = item.timeMap,
                fadeInUs = item.fadeInUs,
                fadeOutUs = item.fadeOutUs,
                sourceRangeUs = sourceRangeUs,
                presentationDurationUs = item.presentationDurationUs,
            ),
        ) { "freeze time map is not supported by the Media3 frame-hold backend" }

        val pieces = mutableListOf<Media3FreezePiece>()
        var cursorPresentationUs = 0L
        item.timeMap.freezeSegments.sortedBy { it.startOffsetUs }.forEach { freeze ->
            if (freeze.startOffsetUs > cursorPresentationUs) {
                pieces += movingPiece(
                    item = item,
                    sourceRangeUs = sourceRangeUs,
                    startPresentationUs = cursorPresentationUs,
                    endPresentationUs = freeze.startOffsetUs,
                )
            }

            pieces += holdPiece(
                item = item,
                fps = fps,
                sourceRangeUs = sourceRangeUs,
                startPresentationUs = freeze.startOffsetUs,
                holdDurationUs = freeze.durationUs,
                sourceOffsetUs = freeze.sourceOffsetUs,
            )
            cursorPresentationUs = Math.addExact(freeze.startOffsetUs, freeze.durationUs)
        }

        if (cursorPresentationUs < item.presentationDurationUs) {
            pieces += movingPiece(
                item = item,
                sourceRangeUs = sourceRangeUs,
                startPresentationUs = cursorPresentationUs,
                endPresentationUs = item.presentationDurationUs,
            )
        }

        require(pieces.isNotEmpty()) { "freeze planner produced no media pieces" }
        val expandedDurationUs = pieces.fold(0L) { totalUs, piece ->
            Math.addExact(totalUs, piece.presentationDurationUs)
        }
        require(expandedDurationUs == item.presentationDurationUs) {
            "freeze planner duration mismatch"
        }
        return pieces
    }

    private fun movingRangesAdvance(
        timeMap: TimeMap,
        sourceRangeUs: Long,
        presentationDurationUs: Long,
    ): Boolean {
        var cursorUs = 0L
        timeMap.freezeSegments.sortedBy { it.startOffsetUs }.forEach { freeze ->
            if (freeze.startOffsetUs > cursorUs) {
                val sourceStartUs = TimeMapEvaluator.forwardSourceOffsetAt(timeMap, cursorUs).coerceAtMost(sourceRangeUs)
                val sourceEndUs = TimeMapEvaluator.forwardSourceOffsetAt(timeMap, freeze.startOffsetUs).coerceAtMost(sourceRangeUs)
                if (sourceEndUs <= sourceStartUs) return false
            }
            cursorUs = runCatching { Math.addExact(freeze.startOffsetUs, freeze.durationUs) }.getOrNull()
                ?: return false
        }
        if (cursorUs < presentationDurationUs) {
            val sourceStartUs = TimeMapEvaluator.forwardSourceOffsetAt(timeMap, cursorUs).coerceAtMost(sourceRangeUs)
            val sourceEndUs = TimeMapEvaluator.forwardSourceOffsetAt(timeMap, presentationDurationUs).coerceAtMost(sourceRangeUs)
            if (sourceEndUs <= sourceStartUs) return false
        }
        return true
    }

    private fun movingPiece(
        item: Media3BlueprintItem.Media,
        sourceRangeUs: Long,
        startPresentationUs: Long,
        endPresentationUs: Long,
    ): Media3FreezePiece {
        val presentationDurationUs = endPresentationUs - startPresentationUs
        require(presentationDurationUs > 0L) { "moving freeze piece must have positive duration" }

        val sourceStartOffsetUs = TimeMapEvaluator
            .forwardSourceOffsetAt(item.timeMap, startPresentationUs)
            .coerceIn(0L, sourceRangeUs)
        val sourceEndOffsetUs = TimeMapEvaluator
            .forwardSourceOffsetAt(item.timeMap, endPresentationUs)
            .coerceIn(0L, sourceRangeUs)
        val sourceDurationUs = sourceEndOffsetUs - sourceStartOffsetUs
        require(sourceDurationUs > 0L) { "moving freeze piece must advance source time" }

        val speed = sourceDurationUs.toDouble() / presentationDurationUs.toDouble()
        require(speed.isFinite() && speed > 0.0) { "moving freeze piece speed must be finite and positive" }

        return Media3FreezePiece(
            sourceInUs = Math.addExact(item.sourceInUs, sourceStartOffsetUs),
            sourceOutUs = Math.addExact(item.sourceInUs, sourceEndOffsetUs),
            presentationDurationUs = presentationDurationUs,
            clipLocalStartUs = startPresentationUs,
            outputTracks = item.outputTracks,
            timeMap = TimeMap(baseSpeed = speed),
            isFreezeHold = false,
        )
    }

    private fun holdPiece(
        item: Media3BlueprintItem.Media,
        fps: Int,
        sourceRangeUs: Long,
        startPresentationUs: Long,
        holdDurationUs: Long,
        sourceOffsetUs: Long,
    ): Media3FreezePiece {
        val boundedSourceOffsetUs = sourceOffsetUs.coerceIn(0L, sourceRangeUs)
        val requestedSourceUs = Math.addExact(item.sourceInUs, boundedSourceOffsetUs)
        val nominalFrameUs = (1_000_000L / fps.toLong()).coerceAtLeast(1L)
        val frameWindowUs = (nominalFrameUs - 1L).coerceAtLeast(1L)

        val sourceInUs: Long
        val sourceOutUs: Long
        if (requestedSourceUs >= item.sourceOutUs) {
            sourceOutUs = item.sourceOutUs
            val availableUs = Math.subtractExact(sourceOutUs, item.sourceInUs)
            sourceInUs = Math.subtractExact(sourceOutUs, minOf(frameWindowUs, availableUs))
        } else {
            sourceInUs = requestedSourceUs.coerceAtLeast(item.sourceInUs)
            val remainingUs = Math.subtractExact(item.sourceOutUs, sourceInUs)
            sourceOutUs = Math.addExact(sourceInUs, minOf(frameWindowUs, remainingUs))
        }
        require(sourceOutUs > sourceInUs) { "freeze hold must reference a positive source-frame window" }

        val sourceDurationUs = sourceOutUs - sourceInUs
        val speed = sourceDurationUs.toDouble() / holdDurationUs.toDouble()
        require(speed.isFinite() && speed > 0.0) { "freeze hold speed must be finite and positive" }

        return Media3FreezePiece(
            sourceInUs = sourceInUs,
            sourceOutUs = sourceOutUs,
            presentationDurationUs = holdDurationUs,
            clipLocalStartUs = startPresentationUs,
            outputTracks = Media3OutputTracks.VIDEO_ONLY,
            timeMap = TimeMap(baseSpeed = speed),
            isFreezeHold = true,
        )
    }
}
