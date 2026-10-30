package com.localcut.editor.bridge.agent

import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.ExportController
import com.localcut.editor.media.ExportDestination
import com.localcut.editor.media.ExportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun interface BridgeExportTargetFactory {
    fun create(project: Project): Result<BridgeExportTarget>
}

data class BridgeExportTarget(
    val destination: ExportDestination,
    val resultJson: String,
)

class Media3BridgeExportPort(
    private val exportController: ExportController,
    private val scope: CoroutineScope,
    private val targetFactory: BridgeExportTargetFactory,
) : BridgeExportPort {
    private val lock = Any()
    private var activeJob: Job? = null

    override fun start(project: Project, listener: BridgeExportListener): Result<Unit> {
        val target = targetFactory.create(project).getOrElse { return Result.failure(it) }
        synchronized(lock) {
            if (activeJob?.isActive == true) {
                return Result.failure(IllegalStateException("another bridge export is already running"))
            }
            activeJob = scope.launch {
                try {
                    exportController.export(project, target.destination).collect { event ->
                        when (event) {
                            ExportEvent.Started -> listener.onStarted()
                            is ExportEvent.Progress -> listener.onProgress(event.percent.coerceIn(0, 100))
                            ExportEvent.Completed -> listener.onCompleted(target.resultJson)
                            ExportEvent.Cancelled -> listener.onCancelled()
                            is ExportEvent.Failed -> listener.onFailed(event.message)
                        }
                    }
                } catch (error: Throwable) {
                    listener.onFailed(error.message ?: "bridge export failed")
                } finally {
                    synchronized(lock) {
                        activeJob = null
                    }
                }
            }
        }
        return Result.success(Unit)
    }

    override fun cancel(): Result<Unit> = runCatching {
        exportController.cancel()
    }
}
