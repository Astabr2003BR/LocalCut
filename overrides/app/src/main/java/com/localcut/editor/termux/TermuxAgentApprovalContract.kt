package com.localcut.editor.termux

data class TermuxAgentApproval(
    val id: String,
    val operation: String,
    val status: String,
    val requestedAt: Long,
)

enum class TermuxApprovalDecision { APPROVE, DENY }

interface TermuxAgentApprovalClient {
    suspend fun getPendingApprovals(): Result<List<TermuxAgentApproval>>
    suspend fun resolveApproval(
        approvalId: String,
        decision: TermuxApprovalDecision,
    ): Result<TermuxAgentApproval>
}

object TermuxAgentApprovalProtocol {
    const val PORT = 43128
    const val MAX_RESPONSE_BYTES = 256 * 1024
}
