package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeErrorCode
import java.util.UUID

enum class BridgeJobState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class BridgeJobSnapshot(
    val jobId: String,
    val messageId: String,
    val state: BridgeJobState,
    val progressPercent: Int? = null,
    val errorCode: BridgeErrorCode? = null,
    val message: String? = null,
    val resultJson: String? = null,
)

sealed interface BridgeJobReservation {
    data class Started(val job: BridgeJobSnapshot) : BridgeJobReservation
    data class Existing(val job: BridgeJobSnapshot) : BridgeJobReservation
    data class Busy(val activeJob: BridgeJobSnapshot) : BridgeJobReservation
}

class BridgeJobRegistry {
    private val jobs = LinkedHashMap<String, BridgeJobSnapshot>()
    private val messageToJobId = mutableMapOf<String, String>()
    private var activeJobId: String? = null

    @Synchronized
    fun reserveExport(messageId: String): BridgeJobReservation {
        messageToJobId[messageId]?.let { jobId ->
            jobs[jobId]?.let { return BridgeJobReservation.Existing(it) }
        }
        activeJobId?.let { jobId ->
            jobs[jobId]?.let { active ->
                if (!active.state.isTerminal()) return BridgeJobReservation.Busy(active)
            }
        }
        val job = BridgeJobSnapshot(
            jobId = UUID.randomUUID().toString(),
            messageId = messageId,
            state = BridgeJobState.QUEUED,
            progressPercent = 0,
        )
        jobs[job.jobId] = job
        messageToJobId[messageId] = job.jobId
        activeJobId = job.jobId
        return BridgeJobReservation.Started(job)
    }

    @Synchronized
    fun get(jobId: String): BridgeJobSnapshot? = jobs[jobId]

    @Synchronized
    fun active(): BridgeJobSnapshot? = activeJobId?.let(jobs::get)?.takeUnless { it.state.isTerminal() }

    @Synchronized
    fun markRunning(jobId: String) {
        update(jobId) { it.copy(state = BridgeJobState.RUNNING, progressPercent = it.progressPercent ?: 0) }
    }

    @Synchronized
    fun updateProgress(jobId: String, percent: Int) {
        require(percent in 0..100) { "progress must be in 0..100" }
        update(jobId) { snapshot ->
            if (snapshot.state.isTerminal()) snapshot else snapshot.copy(
                state = BridgeJobState.RUNNING,
                progressPercent = percent,
            )
        }
    }

    @Synchronized
    fun succeed(jobId: String, resultJson: String) {
        update(jobId) { it.copy(
            state = BridgeJobState.SUCCEEDED,
            progressPercent = 100,
            resultJson = resultJson,
            errorCode = null,
            message = null,
        ) }
        clearActiveIf(jobId)
    }

    @Synchronized
    fun fail(jobId: String, code: BridgeErrorCode, message: String) {
        update(jobId) { it.copy(
            state = BridgeJobState.FAILED,
            errorCode = code,
            message = message.take(500),
        ) }
        clearActiveIf(jobId)
    }

    @Synchronized
    fun cancel(jobId: String) {
        update(jobId) { snapshot ->
            if (snapshot.state.isTerminal()) snapshot else snapshot.copy(state = BridgeJobState.CANCELLED)
        }
        clearActiveIf(jobId)
    }

    private fun update(jobId: String, transform: (BridgeJobSnapshot) -> BridgeJobSnapshot) {
        val current = jobs[jobId] ?: throw IllegalArgumentException("job $jobId not found")
        jobs[jobId] = transform(current)
    }

    private fun clearActiveIf(jobId: String) {
        if (activeJobId == jobId) activeJobId = null
    }

    private fun BridgeJobState.isTerminal(): Boolean = when (this) {
        BridgeJobState.SUCCEEDED, BridgeJobState.FAILED, BridgeJobState.CANCELLED -> true
        BridgeJobState.QUEUED, BridgeJobState.RUNNING -> false
    }
}
