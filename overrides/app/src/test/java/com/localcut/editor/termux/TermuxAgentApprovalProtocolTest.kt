package com.localcut.editor.termux

import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxAgentApprovalProtocolTest {
    @Test
    fun pendingRequestIsAuthenticatedAndTyped() {
        val raw = TermuxAgentApprovalRequestEncoder.pending("request-12345678", "t".repeat(43))
        assertTrue(raw.contains("\"operation\":\"GET_PENDING_APPROVALS\""))
        assertTrue(raw.contains("\"projectId\":\"localcut\""))
        assertFalse(raw.contains("EXEC"))
    }

    @Test
    fun parsesPendingAndResolveApproval() {
        val pendingRoot = MiniJson.parse(
            """{"ok":true,"protocolVersion":1,"requestId":"request-12345678","approvals":[{"id":"12345678-1234-1234-1234-123456789abc","jobId":"task-12345678","projectId":"localcut","operation":"MERGE_MAIN","status":"PENDING","requestedAt":123,"resolvedAt":null,"payload":{},"checkpoint":{}}]}""",
        ).asObject()
        val pending = TermuxAgentApprovalResponseParser.parsePending(pendingRoot)
        assertEquals(1, pending.size)
        assertEquals("MERGE_MAIN", pending.single().operation)
        assertEquals("PENDING", pending.single().status)

        val resolvedRoot = MiniJson.parse(
            """{"ok":true,"protocolVersion":1,"requestId":"request-12345678","approval":{"id":"12345678-1234-1234-1234-123456789abc","jobId":"task-12345678","projectId":"localcut","operation":"MERGE_MAIN","status":"DENIED","requestedAt":123,"resolvedAt":456,"payload":{},"checkpoint":{}}}""",
        ).asObject()
        assertEquals("DENIED", TermuxAgentApprovalResponseParser.parseApprovalResponse(resolvedRoot).status)
    }
}
