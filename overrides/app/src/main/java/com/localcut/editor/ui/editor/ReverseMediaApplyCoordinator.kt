package com.localcut.editor.ui.editor

import com.localcut.editor.domain.history.ApplyReversedAssetCommand
import com.localcut.editor.domain.history.EditorCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.time.ReverseApplyGuard
import java.io.File
import kotlinx.coroutines.CancellationException

data class PreparedReverseAsset(
    val asset: Asset,
    val backingFile: File,
)

class ReverseMediaApplyCoordinator(
    private val currentProject: () -> Project,
    private val prepareReverse: suspend (Asset) -> Result<PreparedReverseAsset>,
    private val execute: (EditorCommand) -> Unit,
) {
    suspend fun reverse(clipId: String): Result<PreparedReverseAsset> = try {
        val startProject = currentProject()
        val snapshot = ReverseApplyGuard.capture(startProject, clipId).getOrThrow()
        val sourceAsset = startProject.assets.firstOrNull { it.id == snapshot.assetId }
            ?: error("REVERSE_SOURCE_ASSET_NOT_FOUND")
        val prepared = prepareReverse(sourceAsset).getOrThrow()

        if (!ReverseApplyGuard.isStillCurrent(currentProject(), snapshot)) {
            prepared.backingFile.delete()
            error("REVERSE_RESULT_STALE")
        }

        try {
            execute(
                ApplyReversedAssetCommand(
                    clipId = snapshot.clipId,
                    reversedAsset = prepared.asset,
                ),
            )
        } catch (error: Throwable) {
            prepared.backingFile.delete()
            throw error
        }

        if (currentProject().findClip(snapshot.clipId)?.assetId != prepared.asset.id) {
            prepared.backingFile.delete()
            error("REVERSE_APPLY_REJECTED")
        }
        Result.success(prepared)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
