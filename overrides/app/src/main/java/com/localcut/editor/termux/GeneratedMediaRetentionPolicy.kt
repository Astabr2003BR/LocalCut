package com.localcut.editor.termux

import java.net.URI

internal object GeneratedMediaRetentionPolicy {
    const val DEFAULT_GRACE_PERIOD_MS: Long = 24L * 60L * 60L * 1_000L

    private val UUID_PATTERN =
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    private val REVERSE_FILE_NAME = Regex("^reverse-$UUID_PATTERN-$UUID_PATTERN\\.mp4$")

    fun isGeneratedReverseName(fileName: String): Boolean =
        REVERSE_FILE_NAME.matches(fileName)

    fun referencedGeneratedFileNames(
        assetUris: Iterable<String>,
        providerAuthority: String,
    ): Set<String> {
        require(providerAuthority.isNotBlank()) { "GENERATED_MEDIA_PROVIDER_AUTHORITY_INVALID" }
        return buildSet {
            assetUris.forEach { rawUri ->
                val fileName = referencedFileName(rawUri, providerAuthority) ?: return@forEach
                if (isGeneratedReverseName(fileName)) add(fileName)
            }
        }
    }

    fun shouldCollect(
        fileName: String,
        lastModifiedMs: Long,
        nowMs: Long,
        protectedFileNames: Set<String>,
        canUndo: Boolean,
        canRedo: Boolean,
        gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
    ): Boolean {
        require(gracePeriodMs > 0L) { "GENERATED_MEDIA_GRACE_PERIOD_INVALID" }
        if (canUndo || canRedo) return false
        if (!isGeneratedReverseName(fileName)) return false
        if (fileName in protectedFileNames) return false
        if (lastModifiedMs <= 0L || nowMs < lastModifiedMs) return false
        val ageMs = nowMs - lastModifiedMs
        return ageMs > gracePeriodMs
    }

    private fun referencedFileName(rawUri: String, providerAuthority: String): String? = runCatching {
        val uri = URI(rawUri)
        if (!uri.scheme.equals("content", ignoreCase = true)) return@runCatching null
        if (uri.authority != providerAuthority) return@runCatching null
        if (uri.query != null || uri.fragment != null) return@runCatching null

        val segments = uri.path
            ?.split('/')
            ?.filter(String::isNotBlank)
            ?: return@runCatching null
        if (segments.size != 2 || segments[0] != "remote-media") return@runCatching null
        segments[1]
    }.getOrNull()
}
