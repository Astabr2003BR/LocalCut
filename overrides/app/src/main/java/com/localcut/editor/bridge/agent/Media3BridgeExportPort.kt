package com.localcut.editor.bridge.agent

import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.ExportController
import com.localcut.editor.media.ExportDestination
import com.localcut.editor.media.ExportEvent
import java.io.File
import java.util.concurrent.atomic.AtomicReference
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
    private val reviewer: BridgeExportFileReviewer? = null,
) : BridgeExportPort {
    private val lock = Any()
    private var activeJob: Job? = null

    override fun start(project: Project, listener: BridgeExportListener): Result<Unit> {
        synchronized(lock) {
            if (activeJob?.isActive == true) {
                return Result.failure(IllegalStateException("another bridge export is already running"))
            }
            val target = targetFactory.create(project).getOrElse { return Result.failure(it) }
            val report = AtomicReference<String?>(null)
            val destination = if (reviewer == null) target.destination else object : ExportDestination {
                override fun commitFrom(tempFile: File): Result<Unit> {
                    // Inspection is best effort and runs on the existing commit worker.
                    // A diagnostic failure must not destroy a valid render.
                    val inspected = runCatching { reviewer.inspect(tempFile, project) }.getOrElse {
                        """{"status":"unavailable","visualQualityVerified":false,"audioSyncVerified":false,"fullDecodeVerified":false}"""
                    }
                    return target.destination.commitFrom(tempFile).also { result ->
                        if (result.isSuccess) report.set(inspected)
                    }
                }
                override fun discard(): Result<Unit> = target.destination.discard()
            }
            activeJob = scope.launch {
                try {
                    exportController.export(project, destination).collect { event ->
                        when (event) {
                            ExportEvent.Started -> listener.onStarted()
                            is ExportEvent.Progress -> listener.onProgress(event.percent.coerceIn(0, 100))
                            ExportEvent.Completed -> {
                                val metadata = report.get()
                                val result = if (reviewer == null) target.resultJson else appendBridgeExportMetadata(
                                    target.resultJson, "\"technicalReview\":" + (metadata ?: """{"status":"unavailable"}"""),
                                )
                                listener.onCompleted(result)
                            }
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
