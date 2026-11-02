package com.localcut.editor.ui.editor

import com.localcut.editor.domain.history.SetClipPropertyAnimationsCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.CompositionRenderResolver
import com.localcut.editor.media.PropertyTransformRenderResolver
import com.localcut.editor.termux.TermuxTrackingQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ObjectTrackingGeometryGateway {
    suspend fun resolve(asset: Asset): Result<ObjectTrackingSourceGeometry>
}

class ObjectTrackingWorkflowCoordinator(
    private val geometryGateway: ObjectTrackingGeometryGateway,
    private val actionCoordinator: ObjectTrackingActionCoordinator,
    private val currentProjectProvider: () -> Project,
) {
    suspend fun buildCommand(
        clipId: String,
        selection: PreviewSelectionRect,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        playheadUs: Long,
        quality: TermuxTrackingQuality = TermuxTrackingQuality.NORMAL,
    ): Result<SetClipPropertyAnimationsCommand> = try {
        Result.success(
            buildCommandOrThrow(
                clipId = clipId,
                selection = selection,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                playheadUs = playheadUs,
                quality = quality,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun buildCommandOrThrow(
        clipId: String,
        selection: PreviewSelectionRect,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        playheadUs: Long,
        quality: TermuxTrackingQuality,
    ): SetClipPropertyAnimationsCommand {
        require(viewportWidthPx > 0 && viewportHeightPx > 0) { "OBJECT_TRACKING_VIEWPORT_INVALID" }
        require(playheadUs >= 0L) { "OBJECT_TRACKING_PLAYHEAD_INVALID" }

        val project = currentProjectProvider()
        require(project.width > 0 && project.height > 0) { "OBJECT_TRACKING_PROJECT_SIZE_INVALID" }
        val clip = requireNotNull(project.findClip(clipId)) { "OBJECT_TRACKING_CLIP_NOT_FOUND" }
        val clipEndUs = Math.addExact(clip.startUs, clip.durationUs)
        require(playheadUs >= clip.startUs && playheadUs < clipEndUs) {
            "OBJECT_TRACKING_PLAYHEAD_OUTSIDE_CLIP"
        }
        require(ObjectTrackingSelectionAnchorPolicy.canCapture(playheadUs, clip, project.fps)) {
            "OBJECT_TRACKING_SELECTION_ANCHOR_REQUIRED"
        }
        val asset = requireNotNull(project.assets.firstOrNull { it.id == clip.assetId }) {
            "OBJECT_TRACKING_ASSET_NOT_FOUND"
        }
        require(asset.type == AssetType.VIDEO && asset.mimeType?.startsWith("video/") == true) {
            "TERMUX_OBJECT_TRACKING_REQUIRES_VIDEO"
        }

        val visualSnapshot = ObjectTrackingVisualSnapshot.capture(project, clipId, asset)
        val capturedClip = ObjectTrackingApplyGuard.capture(clip)

        val sourceGeometry = geometryGateway.resolve(asset).getOrThrow()
        requireVisualStateCurrent(visualSnapshot)

        val resolved = CompositionRenderResolver.resolve(project.compositionGraph, clip).getOrThrow()
        val effectiveTransform = PropertyTransformRenderResolver.valueAt(
            transform = resolved.transform,
            keyframes = resolved.keyframes,
            propertyAnimations = clip.propertyAnimations,
            presentationTimeUs = playheadUs,
            presentationStartUs = clip.startUs,
            compositionContainer = resolved.container,
            compositionContainerTimeline = resolved.containerTimeline,
        )
        val roi = ObjectTrackingRoiMappingPolicy.map(
            selection = selection,
            viewportWidthPx = viewportWidthPx.toDouble(),
            viewportHeightPx = viewportHeightPx.toDouble(),
            sourceWidthPx = sourceGeometry.widthPx,
            sourceHeightPx = sourceGeometry.heightPx,
            outputWidthPx = project.width,
            outputHeightPx = project.height,
            transformSpaceWidthPx = project.width.toDouble(),
            transformSpaceHeightPx = project.height.toDouble(),
            effectiveTransform = effectiveTransform,
            crop = clip.compositing.crop,
        )

        val command = actionCoordinator.buildCommand(
            asset = asset,
            captured = capturedClip,
            roi = roi,
            sourceGeometry = sourceGeometry,
            projectWidth = project.width,
            projectHeight = project.height,
            quality = quality,
        ).getOrThrow()

        // The action coordinator protects mutable clip inputs. This second boundary protects
        // project-level visual inputs (parenting/camera/output size/asset replacement) that also
        // determine which source pixels the user selected in the preview.
        requireVisualStateCurrent(visualSnapshot)
        return command
    }

    private fun requireVisualStateCurrent(snapshot: ObjectTrackingVisualSnapshot) {
        require(snapshot.matches(currentProjectProvider())) { "OBJECT_TRACKING_VISUAL_STATE_STALE" }
    }

    companion object {
        fun fromResolver(
            resolver: ObjectTrackingSourceGeometryResolver,
            actionCoordinator: ObjectTrackingActionCoordinator,
            currentProjectProvider: () -> Project,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): ObjectTrackingWorkflowCoordinator = ObjectTrackingWorkflowCoordinator(
            geometryGateway = object : ObjectTrackingGeometryGateway {
                override suspend fun resolve(asset: Asset): Result<ObjectTrackingSourceGeometry> =
                    withContext(ioDispatcher) { resolver.resolve(asset) }
            },
            actionCoordinator = actionCoordinator,
            currentProjectProvider = currentProjectProvider,
        )
    }
}

private data class ObjectTrackingVisualSnapshot(
    val projectWidth: Int,
    val projectHeight: Int,
    val compositionGraph: CompositionGraph,
    val clip: ObjectTrackingApplySnapshot,
    val asset: Asset,
) {
    fun matches(project: Project): Boolean {
        if (project.width != projectWidth || project.height != projectHeight) return false
        if (project.compositionGraph != compositionGraph) return false
        val currentClip = project.findClip(clip.clipId) ?: return false
        if (!ObjectTrackingApplyGuard.canApply(clip, currentClip)) return false
        return project.assets.firstOrNull { it.id == asset.id } == asset
    }

    companion object {
        fun capture(project: Project, clipId: String, asset: Asset): ObjectTrackingVisualSnapshot {
            val clip = requireNotNull(project.findClip(clipId)) { "OBJECT_TRACKING_CLIP_NOT_FOUND" }
            return ObjectTrackingVisualSnapshot(
                projectWidth = project.width,
                projectHeight = project.height,
                compositionGraph = project.compositionGraph,
                clip = ObjectTrackingApplyGuard.capture(clip),
                asset = asset,
            )
        }
    }
}
