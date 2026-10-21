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
import kotlinx.coroutines.withContext

data class MediaIngestResult(
    val importedCount: Int,
    val failedCount: Int,
    val errors: List<String>,
)

class MediaIngestCoordinator(
    private val importer: AndroidAssetImporter,
    private val editorViewModel: EditorViewModel,
) {
    suspend fun importUris(uris: List<Uri>): MediaIngestResult {
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
                importedCount += 1
            }.onFailure { error ->
                failedCount += 1
                errors += error.message ?: "Não foi possível criar o clipe"
            }
        }

        return MediaIngestResult(
            importedCount = importedCount,
            failedCount = failedCount,
            errors = errors,
        )
    }
}
