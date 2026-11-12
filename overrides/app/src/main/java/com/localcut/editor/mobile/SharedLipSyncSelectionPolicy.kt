package com.localcut.editor.mobile

import com.localcut.editor.domain.model.AssetType

/**
 * Only changes selection for a brand-new two-file lip-sync test (one image, one voice).
 * Existing projects, video shares, partial imports and ambiguous batches are unaffected.
 */
object SharedLipSyncSelectionPolicy {
    data class ImportedClip(
        val clipId: String,
        val assetType: AssetType,
        val startUs: Long,
    )

    fun imageToSelect(emptyBeforeImport: Boolean, added: List<ImportedClip>): String? {
        if (!emptyBeforeImport || added.size != 2) return null
        if (added.any { it.clipId.isBlank() || it.startUs != 0L }) return null
        if (added.map { it.clipId }.distinct().size != 2) return null
        val image = added.singleOrNull { it.assetType == AssetType.IMAGE } ?: return null
        if (added.count { it.assetType == AssetType.AUDIO } != 1) return null
        return image.clipId
    }
}
