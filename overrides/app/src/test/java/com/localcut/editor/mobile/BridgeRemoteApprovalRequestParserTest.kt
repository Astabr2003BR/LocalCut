package com.localcut.editor.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeRemoteApprovalRequestParserTest {
    @Test
    fun validRemoteApprovalParsesRequestId() {
        val requestId = "remote_request_1234567890"
        val parsed = BridgeRemoteApprovalRequestParser.parse(
            "localcut://remote/approve?requestId=$requestId",
        ).getOrThrow()

        assertEquals(requestId, parsed.requestId)
    }

    @Test
    fun wrongSchemeHostOrPathIsRejected() {
        val invalid = listOf(
            "https://remote/approve?requestId=remote_request_1234567890",
            "localcut://oauth/approve?requestId=remote_request_1234567890",
            "localcut://remote/nope?requestId=remote_request_1234567890",
        )

        invalid.forEach { raw ->
            assertTrue(raw, BridgeRemoteApprovalRequestParser.parse(raw).isFailure)
        }
    }

    @Test
    fun missingMalformedOrDuplicateRequestIdIsRejected() {
        val invalid = listOf(
            "localcut://remote/approve",
            "localcut://remote/approve?requestId=short",
            "localcut://remote/approve?requestId=remote_request_1234567890&requestId=remote_request_0987654321",
            "localcut://remote/approve?requestId=remote_request_1234567890&extra=1",
        )

        invalid.forEach { raw ->
            assertTrue(raw, BridgeRemoteApprovalRequestParser.parse(raw).isFailure)
        }
    }
}
