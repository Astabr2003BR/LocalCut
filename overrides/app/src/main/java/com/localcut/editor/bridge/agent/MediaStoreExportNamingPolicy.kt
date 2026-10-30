package com.localcut.editor.bridge.agent

data class MediaStoreExportSpec(
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
)

object MediaStoreExportNamingPolicy {
    fun create(projectTitle: String, nowEpochMs: Long): MediaStoreExportSpec {
        val cleaned = projectTitle
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}._ -]+"), "-")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-', '.', ' ')
            .take(48)
        val displayName = if (cleaned.isBlank()) {
            "LocalCut-$nowEpochMs.mp4"
        } else {
            "LocalCut-$cleaned-$nowEpochMs.mp4"
        }
        return MediaStoreExportSpec(
            displayName = displayName,
            relativePath = "Movies/LocalCut",
            mimeType = "video/mp4",
        )
    }
}
