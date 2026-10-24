package com.localcut.editor.projectpackage

import com.localcut.editor.domain.model.Project

const val LOCALCUT_PACKAGE_SCHEMA_VERSION = 1

data class ProjectPackageAsset(
    val assetId: String,
    val path: String,
    val fingerprint: String,
    val sizeBytes: Long?,
)

data class ProjectPackageManifest(
    val schemaVersion: Int = LOCALCUT_PACKAGE_SCHEMA_VERSION,
    val projectJsonPath: String = "project.json",
    val assets: List<ProjectPackageAsset> = emptyList(),
)

data class ProjectPackageValidationResult(
    val issues: List<String>,
    val missingEntries: List<String>,
)

object ProjectPackagePathPolicy {
    fun isSafe(path: String): Boolean {
        if (path.isBlank() || path.length > 512) return false
        if ('\u0000' in path || '\\' in path) return false
        if (path.startsWith('/')) return false
        if (path.length >= 2 && path[1] == ':' && path[0].isLetter()) return false
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
        return true
    }
}

object ProjectPackagePlanner {
    fun plan(project: Project): ProjectPackageManifest {
        val entries = project.assets
            .sortedBy { it.id }
            .map { asset ->
                val safeId = sanitizeSegment(asset.id.ifBlank { "asset" })
                val safeName = sanitizeFilename(asset.displayName.ifBlank { "asset.bin" })
                val path = "assets/$safeId/$safeName"
                require(ProjectPackagePathPolicy.isSafe(path)) { "generated unsafe package path for ${asset.id}" }
                ProjectPackageAsset(
                    assetId = asset.id,
                    path = path,
                    fingerprint = asset.fingerprint,
                    sizeBytes = asset.sizeBytes,
                )
            }
        return ProjectPackageManifest(assets = entries)
    }

    private fun sanitizeSegment(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('.', '_', '-')
        .ifBlank { "asset" }
        .take(96)

    private fun sanitizeFilename(value: String): String {
        val leaf = value.replace('\\', '/').substringAfterLast('/')
        val normalized = leaf
            .replace(Regex("[^A-Za-z0-9._ -]+"), "_")
            .replace(' ', '_')
            .trim('.', '_', '-')
            .ifBlank { "asset.bin" }
            .take(160)
        return if (normalized == "." || normalized == "..") "asset.bin" else normalized
    }
}

object ProjectPackageValidator {
    fun validate(
        manifest: ProjectPackageManifest,
        availableEntries: Set<String>,
    ): ProjectPackageValidationResult {
        val issues = buildList {
            if (manifest.schemaVersion != LOCALCUT_PACKAGE_SCHEMA_VERSION) {
                add("unsupported project package schema ${manifest.schemaVersion}")
            }
            if (!ProjectPackagePathPolicy.isSafe(manifest.projectJsonPath)) {
                add("unsafe project JSON path")
            }
            if (manifest.assets.map { it.assetId }.distinct().size != manifest.assets.size) {
                add("duplicate asset ids in project package")
            }
            if (manifest.assets.map { it.path }.distinct().size != manifest.assets.size) {
                add("duplicate asset paths in project package")
            }
            manifest.assets.forEach { asset ->
                if (asset.assetId.isBlank()) add("blank asset id in project package")
                if (!ProjectPackagePathPolicy.isSafe(asset.path)) add("unsafe asset path: ${asset.path}")
                if (asset.fingerprint.isBlank()) add("blank fingerprint for asset ${asset.assetId}")
                if (asset.sizeBytes != null && asset.sizeBytes < 0L) add("negative size for asset ${asset.assetId}")
            }
        }

        val required = buildList {
            if (ProjectPackagePathPolicy.isSafe(manifest.projectJsonPath)) add(manifest.projectJsonPath)
            manifest.assets.filter { ProjectPackagePathPolicy.isSafe(it.path) }.forEach { add(it.path) }
        }
        val missing = required.filterNot { it in availableEntries }.sorted()
        return ProjectPackageValidationResult(issues = issues, missingEntries = missing)
    }
}
