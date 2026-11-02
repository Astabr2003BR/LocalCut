package com.localcut.editor.projectpackage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

const val LOCALCUT_PACKAGE_MANIFEST_PATH = "manifest.json"

data class ProjectPackageLimits(
    val maxEntryBytes: Long = 64L * 1024L * 1024L,
    val maxTotalBytes: Long = 256L * 1024L * 1024L,
    val maxEntries: Int = 1_024,
) {
    init {
        require(maxEntryBytes > 0L) { "maxEntryBytes must be positive" }
        require(maxTotalBytes > 0L) { "maxTotalBytes must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }
}

data class ProjectPackageContents(
    val manifest: ProjectPackageManifest,
    val projectJson: String,
    val assets: Map<String, ByteArray>,
    val validation: ProjectPackageValidationResult,
)

object ProjectPackageArchive {
    fun write(
        output: OutputStream,
        manifest: ProjectPackageManifest,
        projectJson: String,
        assetSource: (assetId: String) -> InputStream?,
        limits: ProjectPackageLimits = ProjectPackageLimits(),
    ) {
        require(projectJson.isNotBlank()) { "projectJson must not be blank" }
        val declaredEntries = buildSet {
            add(LOCALCUT_PACKAGE_MANIFEST_PATH)
            add(manifest.projectJsonPath)
            manifest.assets.forEach { add(it.path) }
        }
        val validation = ProjectPackageValidator.validate(manifest, declaredEntries)
        require(validation.issues.isEmpty()) { "invalid project package manifest: ${validation.issues.first()}" }
        require(validation.missingEntries.isEmpty()) { "manifest contains unresolved package paths" }
        require(manifest.projectJsonPath != LOCALCUT_PACKAGE_MANIFEST_PATH) { "project JSON path collides with manifest path" }
        require(manifest.assets.none { it.path == LOCALCUT_PACKAGE_MANIFEST_PATH || it.path == manifest.projectJsonPath }) {
            "asset path collides with package metadata"
        }

        var totalBytes = 0L
        var entryCount = 0
        ZipOutputStream(output).use { zip ->
            fun writeEntry(path: String, input: InputStream) {
                require(ProjectPackagePathPolicy.isSafe(path)) { "unsafe package entry: $path" }
                entryCount++
                require(entryCount <= limits.maxEntries) { "project package contains too many entries" }
                zip.putNextEntry(ZipEntry(path))
                input.use { source ->
                    totalBytes += copyBounded(source, zip, limits.maxEntryBytes, limits.maxTotalBytes - totalBytes)
                }
                zip.closeEntry()
            }

            writeEntry(
                LOCALCUT_PACKAGE_MANIFEST_PATH,
                ByteArrayInputStream(encodeManifest(manifest).toByteArray(Charsets.UTF_8)),
            )
            writeEntry(
                manifest.projectJsonPath,
                ByteArrayInputStream(projectJson.toByteArray(Charsets.UTF_8)),
            )
            manifest.assets.sortedBy { it.path }.forEach { asset ->
                val source = requireNotNull(assetSource(asset.assetId)) {
                    "asset source unavailable: ${asset.assetId}"
                }
                writeEntry(asset.path, source)
            }
        }
    }

    fun read(
        input: InputStream,
        limits: ProjectPackageLimits = ProjectPackageLimits(),
    ): ProjectPackageContents {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        var entryCount = 0

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = entry.name
                require(!entry.isDirectory) { "directory entries are not allowed in LocalCut project packages" }
                require(ProjectPackagePathPolicy.isSafe(path)) { "unsafe project package entry: $path" }
                require(path !in entries) { "duplicate project package entry: $path" }
                entryCount++
                require(entryCount <= limits.maxEntries) { "project package contains too many entries" }

                val bytes = readBounded(zip, limits.maxEntryBytes, limits.maxTotalBytes - totalBytes)
                totalBytes += bytes.size.toLong()
                entries[path] = bytes
                zip.closeEntry()
            }
        }

        val manifestBytes = requireNotNull(entries[LOCALCUT_PACKAGE_MANIFEST_PATH]) {
            "project package manifest is missing"
        }
        val manifest = decodeManifest(manifestBytes.toString(Charsets.UTF_8))
        val validation = ProjectPackageValidator.validate(manifest, entries.keys)
        require(validation.issues.isEmpty()) { "invalid project package manifest: ${validation.issues.first()}" }

        val projectBytes = requireNotNull(entries[manifest.projectJsonPath]) {
            "project JSON is missing from package"
        }
        val assets = linkedMapOf<String, ByteArray>()
        manifest.assets.forEach { asset ->
            entries[asset.path]?.let { bytes ->
                if (asset.sizeBytes != null) {
                    require(bytes.size.toLong() == asset.sizeBytes) {
                        "asset size mismatch for ${asset.assetId}"
                    }
                }
                assets[asset.assetId] = bytes
            }
        }
        return ProjectPackageContents(
            manifest = manifest,
            projectJson = projectBytes.toString(Charsets.UTF_8),
            assets = assets,
            validation = validation,
        )
    }

    private fun copyBounded(
        input: InputStream,
        output: OutputStream,
        maxEntryBytes: Long,
        remainingTotalBytes: Long,
    ): Long {
        require(remainingTotalBytes >= 0L) { "project package exceeds total size limit" }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read.toLong()
            require(copied <= maxEntryBytes) { "project package entry exceeds size limit" }
            require(copied <= remainingTotalBytes) { "project package exceeds total size limit" }
            output.write(buffer, 0, read)
        }
        return copied
    }

    private fun readBounded(
        input: InputStream,
        maxEntryBytes: Long,
        remainingTotalBytes: Long,
    ): ByteArray {
        require(remainingTotalBytes >= 0L) { "project package exceeds total size limit" }
        val output = ByteArrayOutputStream()
        copyBounded(input, output, maxEntryBytes, remainingTotalBytes)
        return output.toByteArray()
    }

    private fun encodeManifest(manifest: ProjectPackageManifest): String = buildJsonObject {
        put("schemaVersion", JsonPrimitive(manifest.schemaVersion))
        put("projectJsonPath", JsonPrimitive(manifest.projectJsonPath))
        put("assets", buildJsonArray {
            manifest.assets.sortedBy { it.assetId }.forEach { asset ->
                add(buildJsonObject {
                    put("assetId", JsonPrimitive(asset.assetId))
                    put("path", JsonPrimitive(asset.path))
                    put("fingerprint", JsonPrimitive(asset.fingerprint))
                    asset.sizeBytes?.let { put("sizeBytes", JsonPrimitive(it)) }
                })
            }
        })
    }.toString()

    private fun decodeManifest(raw: String): ProjectPackageManifest {
        val root = Json.parseToJsonElement(raw).jsonObject
        val schemaVersion = root.requiredPrimitive("schemaVersion").content.toInt()
        val projectJsonPath = root.requiredPrimitive("projectJsonPath").content
        val assets = root["assets"]?.jsonArray ?: JsonArray(emptyList())
        return ProjectPackageManifest(
            schemaVersion = schemaVersion,
            projectJsonPath = projectJsonPath,
            assets = assets.map { value ->
                val asset = value.jsonObject
                ProjectPackageAsset(
                    assetId = asset.requiredPrimitive("assetId").content,
                    path = asset.requiredPrimitive("path").content,
                    fingerprint = asset.requiredPrimitive("fingerprint").content,
                    sizeBytes = asset["sizeBytes"]?.jsonPrimitive?.longOrNull,
                )
            },
        )
    }

    private fun JsonObject.requiredPrimitive(name: String): JsonPrimitive =
        requireNotNull(this[name]?.jsonPrimitive) { "manifest field $name is missing" }
}
