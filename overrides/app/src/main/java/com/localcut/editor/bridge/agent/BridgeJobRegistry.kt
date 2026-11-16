package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.ui.editor.EditorAiReviewSource
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BridgeJobState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class BridgeJobSnapshot(
    val jobId: String,
    val messageId: String,
    val state: BridgeJobState,
    val progressPercent: Int? = null,
    val errorCode: BridgeErrorCode? = null,
    val message: String? = null,
    val resultJson: String? = null,
    val sourceProjectId: String? = null,
    val sourceRevision: Long? = null,
)

sealed interface BridgeJobReservation {
    data class Started(val job: BridgeJobSnapshot) : BridgeJobReservation
    data class Existing(val job: BridgeJobSnapshot) : BridgeJobReservation
    data class Busy(val activeJob: BridgeJobSnapshot) : BridgeJobReservation
}

internal data class BridgeReviewProposal(val id: String, val source: EditorAiReviewSource, val planJson: String)

class BridgeJobRegistry(private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private val jobs = LinkedHashMap<String, BridgeJobSnapshot>()
    private val messageToJobId = mutableMapOf<String, String>()
    private var activeJobId: String? = null
    private var latestEvidence: BridgeExportEvidence? = null
    private var evidenceAtMs: Long = 0L
    private val mutableLatest = MutableStateFlow<BridgeJobSnapshot?>(null)
    val latest: StateFlow<BridgeJobSnapshot?> = mutableLatest.asStateFlow()
    private val mutableReviewProposal = MutableStateFlow<BridgeReviewProposal?>(null)
    internal val reviewProposal: StateFlow<BridgeReviewProposal?> = mutableReviewProposal.asStateFlow()
    private var proposalConsumed = false
    internal var reviewHistory: BridgeReviewHistory? = null
        private set
    @Synchronized
    internal fun attachReviewHistory(history: BridgeReviewHistory) {
        check(reviewHistory == null) { "review history already attached" }
        reviewHistory = history
    }

    @Synchronized
    fun reserveExport(messageId: String, sourceProjectId: String? = null, sourceRevision: Long? = null): BridgeJobReservation {
        messageToJobId[messageId]?.let { jobId ->
            jobs[jobId]?.let { return BridgeJobReservation.Existing(it) }
        }
        activeJobId?.let { jobId ->
            jobs[jobId]?.let { active ->
                if (!active.state.isTerminal()) return BridgeJobReservation.Busy(active)
            }
        }
        latestEvidence = null // retain only the latest export's bounded samples
        reviewHistory?.supersedePending()
        mutableReviewProposal.value = null
        proposalConsumed = false
        val job = BridgeJobSnapshot(
            jobId = UUID.randomUUID().toString(),
            messageId = messageId,
            state = BridgeJobState.QUEUED,
            progressPercent = 0,
            sourceProjectId = sourceProjectId,
            sourceRevision = sourceRevision,
        )
        jobs[job.jobId] = job
        messageToJobId[messageId] = job.jobId
        activeJobId = job.jobId
        mutableLatest.value = job
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
    internal fun succeedWithReview(jobId: String, resultJson: String, projectId: String, revision: Long, review: BridgeExportInspection?) {
        // A late callback from an older job must not replace a newer export's evidence.
        if (activeJobId == jobId && review != null && review.samples.isNotEmpty() && review.sha256 != null) {
            latestEvidence = BridgeExportEvidence(jobId, projectId, revision, review.sha256,
                review.samples.map { it.copy(jpeg = it.jpeg.copyOf()) })
            evidenceAtMs = monotonicMs()
        }
        succeed(jobId, resultJson)
    }

    @Synchronized
    internal fun hasReviewEvidence(source: EditorAiReviewSource): Boolean {
        val evidence = latestEvidence ?: return false
        val age = monotonicMs() - evidenceAtMs
        if (age < 0L || age > 600_000L) {
            latestEvidence = null
            reviewHistory?.supersedePending()
            mutableReviewProposal.value = null
            return false
        }
        return evidence.jobId == source.jobId && evidence.projectId == source.projectId &&
            evidence.revision == source.revision && evidence.sha256 == source.sha256 &&
            mutableLatest.value?.jobId == source.jobId && jobs[source.jobId]?.state == BridgeJobState.SUCCEEDED
    }

    /** One proposal for this export. Never silently replace or requeue a consumed proposal. */
    @Synchronized
    internal fun offerReviewProposal(source: EditorAiReviewSource, planJson: String): BridgeReviewProposal? {
        if (!hasReviewEvidence(source) || proposalConsumed ||
            planJson.isBlank() || planJson.toByteArray(Charsets.UTF_8).size > 8192) return null
        val existing = mutableReviewProposal.value
        if (existing != null) return existing.takeIf { it.source == source && it.planJson == planJson }
        return BridgeReviewProposal(UUID.randomUUID().toString(), source, planJson).also {
            reviewHistory?.received(it)
            mutableReviewProposal.value = it
        }
    }

    @Synchronized
    internal fun takeReviewProposal(id: String, projectId: String, revision: Long): BridgeReviewProposal? {
        val proposal = mutableReviewProposal.value ?: return null
        if (proposal.id != id || proposal.source.projectId != projectId || proposal.source.revision != revision ||
            !hasReviewEvidence(proposal.source)) return null
        mutableReviewProposal.value = null
        proposalConsumed = true
        reviewHistory?.transition(proposal.id, ReviewHistoryStatus.LOADED)
        return proposal
    }

    @Synchronized
    internal fun dismissReviewProposal(id: String): Boolean {
        if (mutableReviewProposal.value?.id != id) return false
        mutableReviewProposal.value = null
        proposalConsumed = true
        reviewHistory?.transition(id, ReviewHistoryStatus.DISCARDED)
        return true
    }

    @Synchronized
    internal fun reviewFrames(jobId: String, projectId: String, revision: Long): String? {
        val evidence = latestEvidence ?: return null
        val age = monotonicMs() - evidenceAtMs
        if (age < 0L || age > 600_000L) {
            latestEvidence = null
            reviewHistory?.supersedePending()
            mutableReviewProposal.value = null
            return null
        }
        if (evidence.jobId != jobId || evidence.projectId != projectId || evidence.revision != revision ||
            jobs[jobId]?.state != BridgeJobState.SUCCEEDED) return null
        return evidence.toJson()
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
        // Terminal callbacks are final: late progress/failure cannot rewrite the UI or job result.
        if (current.state.isTerminal()) return
        val next = transform(current)
        jobs[jobId] = next
        if (mutableLatest.value?.jobId == jobId) mutableLatest.value = next
    }

    private fun clearActiveIf(jobId: String) {
        if (activeJobId == jobId) activeJobId = null
    }

    private fun BridgeJobState.isTerminal(): Boolean = when (this) {
        BridgeJobState.SUCCEEDED, BridgeJobState.FAILED, BridgeJobState.CANCELLED -> true
        BridgeJobState.QUEUED, BridgeJobState.RUNNING -> false
    }
}
