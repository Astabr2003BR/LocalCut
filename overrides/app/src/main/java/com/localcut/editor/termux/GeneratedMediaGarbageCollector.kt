package com.localcut.editor.termux

import java.io.File
import java.nio.file.Files

internal data class GeneratedMediaCleanupResult(
    val blockedByHistory: Boolean,
    val scannedEntries: Int,
    val eligibleCandidates: Int,
    val deletedFiles: Int,
    val failedDeletions: Int,
    val skippedUnsafeEntries: Int,
)

internal class GeneratedMediaGarbageCollector(
    private val remoteMediaDirectory: File,
    private val providerAuthority: String,
    private val gracePeriodMs: Long = GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val deleteFile: (File) -> Boolean = File::delete,
) {
    init {
        require(providerAuthority.isNotBlank()) { "GENERATED_MEDIA_PROVIDER_AUTHORITY_INVALID" }
        require(gracePeriodMs > 0L) { "GENERATED_MEDIA_GRACE_PERIOD_INVALID" }
    }

    fun collect(
        assetUris: Iterable<String>,
        canUndo: Boolean,
        canRedo: Boolean,
    ): Result<GeneratedMediaCleanupResult> = runCatching {
        if (canUndo || canRedo) {
            return@runCatching GeneratedMediaCleanupResult(
                blockedByHistory = true,
                scannedEntries = 0,
                eligibleCandidates = 0,
                deletedFiles = 0,
                failedDeletions = 0,
                skippedUnsafeEntries = 0,
            )
        }

        if (!remoteMediaDirectory.exists()) {
            return@runCatching emptyResult()
        }
        require(!Files.isSymbolicLink(remoteMediaDirectory.toPath())) {
            "GENERATED_MEDIA_DIRECTORY_SYMLINK_REJECTED"
        }
        val root = remoteMediaDirectory.canonicalFile
        require(root.isDirectory) { "GENERATED_MEDIA_DIRECTORY_INVALID" }

        val protectedFileNames = GeneratedMediaRetentionPolicy.referencedGeneratedFileNames(
            assetUris = assetUris,
            providerAuthority = providerAuthority,
        )
        val timestamp = nowMs()
        require(timestamp > 0L) { "GENERATED_MEDIA_CLOCK_INVALID" }

        var scanned = 0
        var eligible = 0
        var deleted = 0
        var failed = 0
        var unsafe = 0

        val entries = root.listFiles() ?: error("GENERATED_MEDIA_DIRECTORY_LIST_FAILED")
        entries.forEach { entry ->
            scanned += 1

            if (Files.isSymbolicLink(entry.toPath())) {
                unsafe += 1
                return@forEach
            }
            if (!entry.isFile || entry.length() <= 0L) {
                unsafe += 1
                return@forEach
            }

            val candidate = entry.canonicalFile
            if (candidate.parentFile != root) {
                unsafe += 1
                return@forEach
            }
            if (!GeneratedMediaRetentionPolicy.shouldCollect(
                    fileName = candidate.name,
                    lastModifiedMs = candidate.lastModified(),
                    nowMs = timestamp,
                    protectedFileNames = protectedFileNames,
                    canUndo = false,
                    canRedo = false,
                    gracePeriodMs = gracePeriodMs,
                )
            ) {
                return@forEach
            }

            eligible += 1
            if (deleteFile(candidate)) {
                deleted += 1
            } else {
                failed += 1
            }
        }

        GeneratedMediaCleanupResult(
            blockedByHistory = false,
            scannedEntries = scanned,
            eligibleCandidates = eligible,
            deletedFiles = deleted,
            failedDeletions = failed,
            skippedUnsafeEntries = unsafe,
        )
    }

    private fun emptyResult() = GeneratedMediaCleanupResult(
        blockedByHistory = false,
        scannedEntries = 0,
        eligibleCandidates = 0,
        deletedFiles = 0,
        failedDeletions = 0,
        skippedUnsafeEntries = 0,
    )
}
