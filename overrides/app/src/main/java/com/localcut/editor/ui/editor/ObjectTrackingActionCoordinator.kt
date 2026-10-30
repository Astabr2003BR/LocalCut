package com.localcut.editor.ui.editor

import com.localcut.editor.domain.history.SetClipPropertyAnimationsCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.termux.TermuxMediaObjectTrackingCoordinator
import com.localcut.editor.termux.TermuxObjectTrackingAnalysis
import com.localcut.editor.termux.TermuxObjectTrackingOptions
import com.localcut.editor.termux.TermuxObjectTrackingRoi
import com.localcut.editor.termux.TermuxTrackingQuality
import com.localcut.editor.termux.TermuxTrackingQualityPolicy
import kotlinx.coroutines.CancellationException

interface ObjectTrackingAnalysisGateway {
    suspend fun analyze(
        asset: Asset,
        options: TermuxObjectTrackingOptions,
    ): Result<TermuxObjectTrackingAnalysis>
}

class ObjectTrackingActionCoordinator(
    private val analysisGateway: ObjectTrackingAnalysisGateway,
    private val currentClipProvider: (String) -> Clip?,
) {
    suspend fun buildCommand(
        asset: Asset,
        captured: ObjectTrackingApplySnapshot,
        roi: NormalizedMediaRoi,
        projectWidth: Int,
        projectHeight: Int,
        quality: TermuxTrackingQuality = TermuxTrackingQuality.NORMAL,
    ): Result<SetClipPropertyAnimationsCommand> = try {
        Result.success(
            buildCommandOrThrow(
                asset = asset,
                captured = captured,
                roi = roi,
                projectWidth = projectWidth,
                projectHeight = projectHeight,
                quality = quality,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun buildCommandOrThrow(
        asset: Asset,
        captured: ObjectTrackingApplySnapshot,
        roi: NormalizedMediaRoi,
        projectWidth: Int,
        projectHeight: Int,
        quality: TermuxTrackingQuality,
    ): SetClipPropertyAnimationsCommand {
        require(asset.id == captured.assetId) { "OBJECT_TRACKING_ASSET_MISMATCH" }
        require(asset.type == AssetType.VIDEO && asset.mimeType?.startsWith("video/") == true) {
            "TERMUX_OBJECT_TRACKING_REQUIRES_VIDEO"
        }
        require(projectWidth > 0 && projectHeight > 0) { "OBJECT_TRACKING_PROJECT_SIZE_INVALID" }

        val startingClip = currentClipProvider(captured.clipId)
        require(ObjectTrackingApplyGuard.canApply(captured, startingClip)) {
            "OBJECT_TRACKING_RESULT_STALE"
        }
        val clip = requireNotNull(startingClip)

        // Fail before staging media or starting OpenCV. These cases cannot produce a safe result.
        ObjectTrackingUiPolicy.requireStartCompatible(clip.propertyAnimations)
        require(!clip.timeMap.reverse) { "OBJECT_TRACKING_REVERSE_UNSUPPORTED" }
        require(clip.timeMap.freezeSegments.isEmpty()) { "OBJECT_TRACKING_FREEZE_UNSUPPORTED" }
        require(clip.sourceInUs >= 0L && clip.sourceOutUs > clip.sourceInUs) {
            "OBJECT_TRACKING_SOURCE_RANGE_INVALID"
        }
        asset.sourceDurationUs?.let { durationUs ->
            require(durationUs > 0L) { "OBJECT_TRACKING_SOURCE_RANGE_INVALID" }
            require(clip.sourceInUs < durationUs && clip.sourceOutUs <= durationUs) {
                "OBJECT_TRACKING_SOURCE_RANGE_INVALID"
            }
        }

        val options = TermuxTrackingQualityPolicy.objectTrackingOptions(
            quality = quality,
            roi = TermuxObjectTrackingRoi(
                x = roi.x,
                y = roi.y,
                width = roi.width,
                height = roi.height,
            ),
            sourceInUs = clip.sourceInUs,
            sourceOutUs = clip.sourceOutUs,
        )

        // One analysis request only. No blind retry: staging/analysis is not repeated here.
        val analysis = analysisGateway.analyze(asset, options).getOrThrow()

        // Re-read mutable editor state after the asynchronous job to catch trim/edit/delete races.
        val currentClip = currentClipProvider(captured.clipId)
            ?: throw IllegalArgumentException("OBJECT_TRACKING_RESULT_STALE")

        return ObjectTrackingApplyCommandFactory.createFromAnalysis(
            captured = captured,
            currentClip = currentClip,
            analysis = analysis,
            projectWidth = projectWidth,
            projectHeight = projectHeight,
        )
    }

    companion object {
        fun fromTermux(
            coordinator: TermuxMediaObjectTrackingCoordinator,
            currentClipProvider: (String) -> Clip?,
        ): ObjectTrackingActionCoordinator = ObjectTrackingActionCoordinator(
            analysisGateway = object : ObjectTrackingAnalysisGateway {
                override suspend fun analyze(
                    asset: Asset,
                    options: TermuxObjectTrackingOptions,
                ): Result<TermuxObjectTrackingAnalysis> = coordinator.analyze(asset, options)
            },
            currentClipProvider = currentClipProvider,
        )
    }
}
