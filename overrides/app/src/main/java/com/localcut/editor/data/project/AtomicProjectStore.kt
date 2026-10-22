package com.localcut.editor.data.project

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineValidator
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

enum class ProjectStoreErrorCode { NOT_FOUND, CORRUPT_PROJECT, UNSUPPORTED_SCHEMA, INVALID_PROJECT, IO_ERROR }

class ProjectStoreException(
    val code: ProjectStoreErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class AtomicProjectStore(private val projectDir: Path) : ProjectStore {
    private val current: Path get() = projectDir.resolve("project.json")
    private val backup: Path get() = projectDir.resolve("project.json.bak")
    private val temp: Path get() = projectDir.resolve("project.json.tmp")
    private val ioLock: Any = lockFor(projectDir)

    override fun save(project: Project): Result<Unit> = synchronized(ioLock) {
        runCatching {
            val issues = TimelineValidator.validate(project)
            if (issues.isNotEmpty()) {
                throw ProjectStoreException(
                    ProjectStoreErrorCode.INVALID_PROJECT,
                    "refusing to save invalid project: ${issues.first()}",
                )
            }
            Files.createDirectories(projectDir)
            writeForced(temp, ProjectJson.encode(project).toByteArray(StandardCharsets.UTF_8))

            if (Files.exists(current) && isValidCurrent(current)) {
                Files.copy(current, backup, StandardCopyOption.REPLACE_EXISTING)
                forceFile(backup)
            }
            replaceAtomically(temp, current)
            forceFile(current)
            forceDirectoryBestEffort(projectDir)
        }.recoverCatching { cause ->
            if (cause is ProjectStoreException) throw cause
            throw ProjectStoreException(ProjectStoreErrorCode.IO_ERROR, "project save failed", cause)
        }
    }

    override fun load(): Result<Project> = synchronized(ioLock) {
        loadLocked()
    }

    private fun loadLocked(): Result<Project> {
        var currentFailure: Throwable? = null
        if (Files.exists(current)) {
            try {
                return Result.success(readProject(current))
            } catch (error: UnsupportedProjectSchemaException) {
                return Result.failure(
                    ProjectStoreException(
                        ProjectStoreErrorCode.UNSUPPORTED_SCHEMA,
                        error.message ?: "unsupported schema",
                        error,
                    ),
                )
            } catch (error: Throwable) {
                currentFailure = error
            }
        }

        recoverValidTemp()?.let { return it }

        if (Files.exists(backup)) {
            return try {
                Result.success(readProject(backup))
            } catch (error: UnsupportedProjectSchemaException) {
                Result.failure(
                    ProjectStoreException(
                        ProjectStoreErrorCode.UNSUPPORTED_SCHEMA,
                        error.message ?: "unsupported schema",
                        error,
                    ),
                )
            } catch (error: Throwable) {
                Result.failure(
                    ProjectStoreException(
                        ProjectStoreErrorCode.CORRUPT_PROJECT,
                        "current and backup project files are unreadable",
                        error,
                    ),
                )
            }
        }

        if (currentFailure != null) {
            return Result.failure(
                ProjectStoreException(
                    ProjectStoreErrorCode.CORRUPT_PROJECT,
                    "project.json is unreadable and no valid recovery snapshot exists",
                    currentFailure,
                ),
            )
        }

        return Result.failure(ProjectStoreException(ProjectStoreErrorCode.NOT_FOUND, "project.json not found"))
    }

    private fun recoverValidTemp(): Result<Project>? {
        if (!Files.exists(temp)) return null
        val recovered = try {
            readProject(temp)
        } catch (error: UnsupportedProjectSchemaException) {
            return Result.failure(
                ProjectStoreException(
                    ProjectStoreErrorCode.UNSUPPORTED_SCHEMA,
                    error.message ?: "unsupported schema",
                    error,
                ),
            )
        } catch (_: Throwable) {
            return null
        }

        try {
            replaceAtomically(temp, current)
            forceFile(current)
            forceDirectoryBestEffort(projectDir)
        } catch (_: Throwable) {
            // The decoded temp snapshot is still usable. Leave it in place when promotion fails
            // so a later load can retry recovery instead of discarding the user's project.
        }
        return Result.success(recovered)
    }

    private fun readProject(path: Path): Project {
        val json = path.toFile()
            .inputStream()
            .bufferedReader(StandardCharsets.UTF_8)
            .use { reader -> reader.readText() }
        return ProjectJson.decode(json)
    }

    private fun isValidCurrent(path: Path): Boolean = try {
        readProject(path)
        true
    } catch (_: Throwable) {
        false
    }

    private fun writeForced(path: Path, bytes: ByteArray) {
        FileChannel.open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private fun forceFile(path: Path) {
        FileChannel.open(path, StandardOpenOption.READ).use { it.force(true) }
    }

    private fun replaceAtomically(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun forceDirectoryBestEffort(dir: Path) {
        try {
            FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Throwable) {
            // Some providers do not allow opening directories as channels; file fsync already completed.
        }
    }

    companion object {
        private val ioLocks = ConcurrentHashMap<String, Any>()

        private fun lockFor(projectDir: Path): Any {
            val key = projectDir.toAbsolutePath().normalize().toString()
            return ioLocks.computeIfAbsent(key) { Any() }
        }

        fun save(projectDir: Path, project: Project): Result<Unit> = AtomicProjectStore(projectDir).save(project)
        fun load(projectDir: Path): Result<Project> = AtomicProjectStore(projectDir).load()
    }
}
