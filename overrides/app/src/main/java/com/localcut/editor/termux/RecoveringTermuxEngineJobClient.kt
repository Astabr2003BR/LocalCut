package com.localcut.editor.termux

import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay

internal object TermuxEndpointFailurePolicy {
    fun isUnavailable(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (
                current is ConnectException ||
                current is SocketTimeoutException ||
                current is NoRouteToHostException ||
                current.message in setOf("TERMUX_ENGINE_UNAVAILABLE", "TERMUX_AGENT_UNAVAILABLE")
            ) return true
            current = current.cause
        }
        return false
    }
}

internal class RecoveringTermuxEngineJobClient(
    private val delegate: TermuxEngineJobClient,
    private val wakeAction: () -> Result<Boolean>,
    private val retryDelayMs: Long = TermuxAutomationPolicy.RECOVERY_RETRY_DELAY_MS,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) : TermuxEngineJobClient {
    override suspend fun pair(pairingCode: String): Result<TermuxEnginePairing> = delegate.pair(pairingCode)

    override suspend fun ping(): Result<TermuxEnginePing> = recoverOnce { delegate.ping() }

    override suspend fun getStatus(): Result<TermuxEngineStatus> = recoverOnce { delegate.getStatus() }

    override suspend fun getCapabilities(): Result<TermuxEngineCapabilities> = recoverOnce { delegate.getCapabilities() }

    override suspend fun createAnalyzeMediaJob(
        input: TermuxAnalyzeMediaInput,
        idempotencyKey: String,
    ): Result<TermuxEngineJob> = recoverOnce {
        delegate.createAnalyzeMediaJob(input, idempotencyKey)
    }

    override suspend fun getJob(jobId: String): Result<TermuxEngineJob> = recoverOnce {
        delegate.getJob(jobId)
    }

    override suspend fun cancelJob(jobId: String): Result<TermuxEngineJob> = delegate.cancelJob(jobId)

    override suspend fun stageMedia(
        jobId: String,
        source: TermuxEngineMediaSource,
    ): Result<TermuxEngineStageResult> = delegate.stageMedia(jobId, source)

    override suspend fun downloadGeneratedMedia(
        jobId: String,
        destination: File,
    ): Result<TermuxGeneratedMediaDownload> = delegate.downloadGeneratedMedia(jobId, destination)

    private suspend fun <T> recoverOnce(action: suspend () -> Result<T>): Result<T> {
        val first = action()
        if (first.isSuccess) return first
        val firstError = first.exceptionOrNull() ?: return first
        if (!TermuxEndpointFailurePolicy.isUnavailable(firstError)) return first
        if (wakeAction().isFailure) return first
        if (retryDelayMs > 0L) sleeper(retryDelayMs)
        return action()
    }
}
