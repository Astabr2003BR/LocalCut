package com.localcut.editor.data.project

import com.localcut.editor.domain.model.Project
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Select the same private project store after process restart. The legacy runtime
 * allocated a new timestamped recovery directory on every failed primary load,
 * making successfully saved recovery edits disappear on the next launch.
 *
 * Never deletes, renames or overwrites a failed/unsupported project snapshot.
 * All candidates come from immediate app-private recovery-* directories only.
 */
internal data class ProjectStartupSelection(
    val store: AtomicProjectStore,
    val directory: Path,
    val project: Project?,
    val warning: String?,
)

internal object ProjectStartupRecovery {
    fun select(projectsRoot: Path): ProjectStartupSelection {
        val primaryDir = projectsRoot.resolve("default")
        val primaryStore = AtomicProjectStore(primaryDir)
        val primary = primaryStore.load()
        if (primary.isSuccess) return ProjectStartupSelection(primaryStore, primaryDir, primary.getOrThrow(), null)

        // Discover both old recovery-<timestamp> and new recovery-<random> folders.
        // A valid current snapshot outranks an empty directory. Candidates are
        // inspected by the existing AtomicProjectStore backup/temp rules.
        val candidates = if (Files.isDirectory(projectsRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.list(projectsRoot).use { stream ->
                stream.iterator().asSequence()
                    .filter { dir ->
                        Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) &&
                            dir.fileName.toString().matches(Regex("recovery-[A-Za-z0-9_-]{1,80}"))
                    }
                    .toList()
            }
        } else emptyList()

        val ranked = candidates.sortedWith(
            compareByDescending<Path> { candidateModifiedAt(it) }
                .thenByDescending { it.fileName.toString() }
        )
        for (dir in ranked) {
            val store = AtomicProjectStore(dir)
            val recovered = store.load()
            if (recovered.isSuccess) {
                return ProjectStartupSelection(
                    store, dir, recovered.getOrThrow(),
                    "Projeto principal indisponível. Recuperação salva reaberta; arquivos anteriores preservados.",
                )
            }
        }

        val error = primary.exceptionOrNull() as? ProjectStoreException
        if (error?.code == ProjectStoreErrorCode.NOT_FOUND && candidates.isEmpty()) {
            // Truly first launch: the normal store remains the sole destination.
            return ProjectStartupSelection(primaryStore, primaryDir, null, null)
        }

        // Primary or previous recoveries may be corrupted or from a newer schema:
        // allocate once, then discover this location on future launches after save.
        // Do not write to a known-bad directory or reset any existing file.
        Files.createDirectories(projectsRoot)
        val freshDir = Files.createTempDirectory(projectsRoot, "recovery-")
        return ProjectStartupSelection(
            AtomicProjectStore(freshDir), freshDir, null,
            "Projeto anterior preservado. Nova área de recuperação criada; verifique seus projetos anteriores.",
        )
    }

    private fun candidateModifiedAt(dir: Path): Long = listOf(
        dir.resolve("project.json"),
        dir.resolve("project.json.tmp"),
        dir.resolve("project.json.bak"),
        dir,
    ).maxOf { file ->
        runCatching {
            Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis()
        }.getOrDefault(Long.MIN_VALUE)
    }
}
