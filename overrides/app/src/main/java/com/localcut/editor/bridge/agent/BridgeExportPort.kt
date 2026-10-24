package com.localcut.editor.bridge.agent

import com.localcut.editor.domain.model.Project

class BridgeLocalInteractionRequiredException(message: String) : IllegalStateException(message)

interface BridgeExportListener {
    fun onStarted()
    fun onProgress(percent: Int)
    fun onCompleted(resultJson: String)
    fun onFailed(message: String)
    fun onCancelled()
}

interface BridgeExportPort {
    fun start(project: Project, listener: BridgeExportListener): Result<Unit>
    fun cancel(): Result<Unit>
}

object UnavailableBridgeExportPort : BridgeExportPort {
    override fun start(project: Project, listener: BridgeExportListener): Result<Unit> =
        Result.failure(IllegalStateException("bridge export port unavailable"))

    override fun cancel(): Result<Unit> =
        Result.failure(IllegalStateException("bridge export port unavailable"))
}
