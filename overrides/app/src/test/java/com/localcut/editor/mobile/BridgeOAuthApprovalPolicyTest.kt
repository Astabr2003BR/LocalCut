package com.localcut.editor.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeOAuthApprovalPolicyTest {
    @Test
    fun parsesExactLocalCutOAuthApprovalDeepLink() {
        val request = BridgeOAuthApprovalRequestParser.parse(
            "localcut://oauth/approve?requestId=req_1234567890abcdef",
        ).getOrThrow()

        assertEquals("req_1234567890abcdef", request.requestId)
    }

    @Test
    fun rejectsWrongSchemeHostPathAndUnexpectedQueryFields() {
        val invalid = listOf(
            "https://oauth/approve?requestId=req_1234567890abcdef",
            "localcut://command/approve?requestId=req_1234567890abcdef",
            "localcut://oauth/wrong?requestId=req_1234567890abcdef",
            "localcut://oauth/approve",
            "localcut://oauth/approve?requestId=short",
            "localcut://oauth/approve?requestId=req_1234567890abcdef&next=https%3A%2F%2Fevil.example",
            "localcut://oauth/approve?requestId=req_1234567890abcdef#fragment",
        )

        invalid.forEach { raw ->
            assertTrue("should reject $raw", BridgeOAuthApprovalRequestParser.parse(raw).isFailure)
        }
    }

    @Test
    fun acceptsOnlyCompletionUrlOnConfiguredRelayForSameRequest() {
        val relay = "https://localcut-bridge-v1.onrender.com"
        val requestId = "req_1234567890abcdef"
        val valid = "$relay/oauth/complete?requestId=$requestId"

        assertEquals(
            valid,
            BridgeOAuthApprovalPolicy.validateCompletionUrl(relay, requestId, valid).getOrThrow(),
        )

        val invalid = listOf(
            "http://localcut-bridge-v1.onrender.com/oauth/complete?requestId=$requestId",
            "https://evil.example/oauth/complete?requestId=$requestId",
            "$relay/oauth/complete?requestId=other_1234567890abcdef",
            "$relay/oauth/complete?requestId=$requestId&next=https%3A%2F%2Fevil.example",
            "$relay/not-complete?requestId=$requestId",
        )
        invalid.forEach { url ->
            assertTrue(
                "should reject $url",
                BridgeOAuthApprovalPolicy.validateCompletionUrl(relay, requestId, url).isFailure,
            )
        }
    }
}
