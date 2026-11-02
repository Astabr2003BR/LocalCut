package com.localcut.editor.ui.editor

import com.localcut.editor.domain.history.ApplyExtractedAudioCommand
import com.localcut.editor.domain.history.EditorCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.time.AudioExtractApplyGuard
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class PreparedExtractedAudioAsset(
    val asset: Asset,
    val backingFile: File,
)

class ExtractAudioApplyCoordinator(
    private val currentProject: () -> Project,
    private val prepareAudio: suspend (Asset) -> Result<PreparedExtractedAudioAsset>,
    private val execute: (EditorCommand) -> Unit,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun extract(clipId: String): Result<PreparedExtractedAudioAsset> = try {
        val startProject = currentProject()
        val snapshot = AudioExtractApplyGuard.capture(startProject, clipId).getOrThrow()
        val sourceAsset = startProject.assets.firstOrNull { it.id == snapshot.assetId }
            ?: error("EXTRACT_AUDIO_SOURCE_ASSET_NOT_FOUND")
        val prepared = prepareAudio(sourceAsset).getOrThrow()

        if (!AudioExtractApplyGuard.isStillCurrent(currentProject(), snapshot)) {
            prepared.backingFile.delete()
            error("EXTRACT_AUDIO_RESULT_STALE")
        }

        val extractedClipId = "audio-clip-${idFactory()}"
        val extractedTrackId = "audio-track-${idFactory()}"
        try {
            execute(
                ApplyExtractedAudioCommand(
                    sourceClipId = snapshot.clipId,
                    extractedAsset = prepared.asset,
                    extractedClipId = extractedClipId,
                    extractedTrackId = extractedTrackId,
                ),
            )
        } catch (error: Throwable) {
            prepared.backingFile.delete()
            throw error
        }

        val project = currentProject()
        val appliedAudio = project.findClip(extractedClipId)
        val source = project.findClip(snapshot.clipId)
        if (appliedAudio?.assetId != prepared.asset.id || source?.volume != 0.0) {
            prepared.backingFile.delete()
            error("EXTRACT_AUDIO_APPLY_REJECTED")
        }
        Result.success(prepared)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
