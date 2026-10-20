package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import java.io.File
import kotlinx.coroutines.flow.Flow

sealed interface ExportEvent {
    data object Started : ExportEvent
    data class Progress(val percent: Int) : ExportEvent
    data object Completed : ExportEvent
    data object Cancelled : ExportEvent
    data class Failed(val message: String, val cause: Throwable? = null) : ExportEvent
}

fun interface ExportDestination {
    fun commitFrom(tempFile: File): Result<Unit>
    fun discard(): Result<Unit> = Result.success(Unit)
}

interface ExportController {
    fun export(project: Project, destination: ExportDestination): Flow<ExportEvent>

    fun export(
        project: Project,
        destination: ExportDestination,
        profile: ExportProfile,
    ): Flow<ExportEvent> = export(project, destination)

    fun cancel()
}

class ExportLifecycleCoordinator(
    private val tempFile: File,
    private val destination: ExportDestination,
    private val emit: (ExportEvent) -> Unit,
) {
    private enum class State { NEW, RENDERING, COMMITTING, TERMINAL }

    private var state = State.NEW
    private var lastProgress: Int? = null

    @Synchronized
    fun started() {
        if (state != State.NEW) return
        state = State.RENDERING
        emit(ExportEvent.Started)
    }

    @Synchronized
    fun progress(percent: Int) {
        if (state != State.RENDERING) return
        val clamped = percent.coerceIn(0, 100)
        if (lastProgress == clamped) return
        lastProgress = clamped
        emit(ExportEvent.Progress(clamped))
    }

    fun renderCompleted() {
        synchronized(this) {
            if (state != State.RENDERING) return
            state = State.COMMITTING
        }
        val result = runCatching { destination.commitFrom(tempFile).getOrThrow() }
        synchronized(this) {
            if (state != State.COMMITTING) return
            if (result.isSuccess) {
                emit(ExportEvent.Completed)
            } else {
                discardDestinationSafely()
                emit(ExportEvent.Failed("Unable to commit export destination", result.exceptionOrNull()))
            }
            terminalCleanup()
        }
    }

    @Synchronized
    fun renderFailed(error: Throwable) {
        if (state != State.RENDERING) return
        discardDestinationSafely()
        emit(ExportEvent.Failed(error.message ?: "Media3 export failed", error))
        terminalCleanup()
    }

    @Synchronized
    fun cancel() {
        if (state != State.NEW && state != State.RENDERING) return
        discardDestinationSafely()
        emit(ExportEvent.Cancelled)
        terminalCleanup()
    }

    @Synchronized
    fun isTerminal(): Boolean = state == State.TERMINAL

    private fun discardDestinationSafely() {
        runCatching { destination.discard().getOrThrow() }
    }

    private fun terminalCleanup() {
        runCatching { tempFile.delete() }
        state = State.TERMINAL
    }
}
