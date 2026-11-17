package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.BridgeReviewHistory
import com.localcut.editor.bridge.agent.ReviewHistoryStatus
import com.localcut.editor.session.ProjectSession

internal data class EditorAiApplyPersistenceResult(val revision: Long, val saved: Boolean)

/**
 * Distinguishes an in-memory, undoable editor change from a confirmed atomic
 * project-store save. A disk failure must never be reported as a rejected edit.
 * Called by the local approval UI, never by a remote Bridge command.
 */
internal class EditorAiApplyPersistence(
    private val history: BridgeReviewHistory?,
    private val persist: () -> Result<Unit>,
) {
    fun apply(
        gate: EditorAiApprovalGate,
        approval: EditorAiApprovalGate.Approval,
        draft: String,
        session: ProjectSession,
        source: EditorAiReviewSource?,
        proposalId: String?,
    ): Result<EditorAiApplyPersistenceResult> =
        gate.apply(approval, draft, session, source).map { revision ->
            proposalId?.let { history?.transition(it, ReviewHistoryStatus.APPLIED_IN_EDITOR, revision) }
            EditorAiApplyPersistenceResult(revision, confirmPersist(proposalId, revision))
        }

    /** Retry only for an unchanged revision; never certify a different edit. */
    fun retry(proposalId: String?, expectedRevision: Long, session: ProjectSession): Boolean =
        session.state.revision == expectedRevision && confirmPersist(proposalId, expectedRevision)

    private fun confirmPersist(proposalId: String?, revision: Long): Boolean {
        // No automatic rollback: the already-applied edit remains available for Undo
        // and the debounced saver retains failed writes for retry.
        val saved = runCatching { persist().isSuccess }.getOrDefault(false)
        if (saved) proposalId?.let { history?.transition(it, ReviewHistoryStatus.SAVED_TO_DISK, revision) }
        return saved
    }
}
