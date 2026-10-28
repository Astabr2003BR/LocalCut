package com.localcut.editor.mobile

import android.net.Uri
import com.localcut.editor.data.assets.AndroidAssetImporter
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreparedGeneratedVideo(
    val uri: Uri,
    val backingFile: File,
    val asset: Asset,
)

class GeneratedMediaAssetInspector(
    private val importer: AndroidAssetImporter,
    private val sharedMediaStager: SharedMediaStager,
) {
    suspend fun inspect(backingFile: File): Result<PreparedGeneratedVideo> = runCatching {
        val exposed = sharedMediaStager.exposeOwnedFile(backingFile).getOrThrow()
        val asset = withContext(Dispatchers.IO) {
            importer.import(exposed.uri).getOrThrow()
        }
        require(asset.type == AssetType.VIDEO) { "GENERATED_MEDIA_NOT_VIDEO" }
        require(asset.mimeType?.startsWith("video/") == true) { "GENERATED_MEDIA_MIME_INVALID" }
        require(asset.sizeBytes?.takeIf { it > 0L } != null) { "GENERATED_MEDIA_SIZE_INVALID" }
        require(asset.sizeBytes == exposed.backingFile.length()) { "GENERATED_MEDIA_SIZE_MISMATCH" }
        require(asset.sourceDurationUs?.takeIf { it > 0L } != null) { "GENERATED_MEDIA_DURATION_INVALID" }
        PreparedGeneratedVideo(
            uri = exposed.uri,
            backingFile = exposed.backingFile,
            asset = asset,
        )
    }
}
