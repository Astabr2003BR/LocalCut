package com.localcut.editor.mobile

import android.net.Uri
import com.localcut.editor.data.assets.AndroidAssetImporter
import com.localcut.editor.data.assets.ImportedClipFactory
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.EditorAction
import com.localcut.editor.ui.editor.EditorViewModel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class MediaIngestResult(
    val importedCount: Int,
    val failedCount: Int,
    val errors: List<String>,
)

class MediaIngestCoordinator(
    private val importer: AndroidAssetImporter,
    private val editorViewModel: EditorViewModel,
    private val sharedMediaStager: SharedMediaStager,
    private val persistProject: () -> Result<Unit> = { Result.success(Unit) },
) {
    /**
     * User-selected external media must become LocalCut-owned before it is added to the project.
     * This avoids keeping transient provider URIs that can stop resolving after process/device
     * restarts or later provider permission changes.
     */
    suspend fun importUris(uris: List<Uri>): MediaIngestResult = importStagedUris(
        uris = uris,
        stageFailureMessage = "Não foi possível preservar a mídia importada",
    )

    suspend fun importSharedUris(uris: List<Uri>): MediaIngestResult = importStagedUris(
        uris = uris,
        stageFailureMessage = "Não foi possível preservar a mídia compartilhada",
    )

    /**
     * Imports media that is already backed by LocalCut-owned storage (for example remote-media).
     * Callers are responsible for retaining/deleting that backing according to the returned result.
     */
    suspend fun importOwnedUris(uris: List<Uri>): MediaIngestResult = importPreparedUris(uris)

    private suspend fun importStagedUris(
        uris: List<Uri>,
        stageFailureMessage: String,
    ): MediaIngestResult {
        var importedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()

        for (source in uris) {
            val stagedResult = sharedMediaStager.stage(source)
            if (stagedResult.isFailure) {
                failedCount += 1
                errors += stagedResult.exceptionOrNull()?.message ?: stageFailureMessage
                continue
            }

            val staged = stagedResult.getOrThrow()
            var retainBacking = false
            try {
                val result = importPreparedUris(listOf(staged.uri))
                importedCount += result.importedCount
                failedCount += result.failedCount
                errors += result.errors
                // If the clip made it into the project, retain its backing even when persistence
                // reports an error so a later autosave/recovery can still reference valid bytes.
                retainBacking = result.importedCount == 1 && result.failedCount == 0
            } finally {
                if (!retainBacking) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        staged.backingFile.delete()
                    }
                }
            }
        }

        return MediaIngestResult(
            importedCount = importedCount,
            failedCount = failedCount,
            errors = errors,
        )
    }

    private suspend fun importPreparedUris(uris: List<Uri>): MediaIngestResult {
        var importedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()

        for (uri in uris) {
            val importResult = withContext(Dispatchers.IO) { importer.import(uri) }
            if (importResult.isFailure) {
                failedCount += 1
                errors += importResult.exceptionOrNull()?.message ?: "Não foi possível importar a mídia"
                continue
            }

            val asset = importResult.getOrThrow()
            val project = editorViewModel.sessionState.value.project
            val targetType = if (asset.type == AssetType.AUDIO) TrackType.AUDIO else TrackType.VISUAL
            val track = project.tracks.firstOrNull { it.type == targetType }
            if (track == null) {
                failedCount += 1
                errors += "Trilha ${targetType.name.lowercase()} não encontrada"
                continue
            }

            val startUs = track.clips.maxOfOrNull { it.startUs + it.durationUs } ?: 0L
            val clipResult = ImportedClipFactory.create(
                asset = asset,
                clipId = UUID.randomUUID().toString(),
                startUs = startUs,
            )
            clipResult.onSuccess { clip ->
                editorViewModel.dispatch(EditorAction.AddImportedClip(track.id, asset, clip))
                if (editorViewModel.sessionState.value.project.findClip(clip.id) != null) {
                    importedCount += 1
                } else {
                    failedCount += 1
                    errors += editorViewModel.sessionState.value.lastError ?: "Não foi possível inserir a mídia"
                }
            }.onFailure { error ->
                failedCount += 1
                errors += error.message ?: "Não foi possível criar o clipe"
            }
        }

        if (importedCount > 0) {
            val persistenceResult = withContext(Dispatchers.IO) { persistProject() }
            persistenceResult.exceptionOrNull()?.let { error ->
                errors += "Mídia importada, mas o projeto ainda não pôde ser salvo: ${error.message ?: error::class.simpleName}"
            }
        }

        return MediaIngestResult(
            importedCount = importedCount,
            failedCount = failedCount,
            errors = errors,
        )
    }
}
