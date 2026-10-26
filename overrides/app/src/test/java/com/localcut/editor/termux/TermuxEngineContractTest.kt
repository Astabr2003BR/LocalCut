package com.localcut.editor.termux

import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineContractTest {
    private val token = "a".repeat(43)

    @Test
    fun endpointAndTokenPoliciesFailClosed() {
        TermuxEngineEndpointPolicy.validate("127.0.0.1", 43127)
        assertTrue(runCatching { TermuxEngineEndpointPolicy.validate("0.0.0.0", 43127) }.isFailure)
        assertTrue(runCatching { TermuxEngineEndpointPolicy.validate("localhost", 43127) }.isFailure)
        assertTrue(runCatching { TermuxEngineEndpointPolicy.validate("127.0.0.1", 0) }.isFailure)
        assertEquals(token, TermuxEngineTokenPolicy.normalize(token))
        assertTrue(runCatching { TermuxEngineTokenPolicy.normalize("short") }.isFailure)
    }

    @Test
    fun pairingCodePolicyNormalizesHumanFormatAndRejectsWeakCodes() {
        assertEquals("23456789ABCD", TermuxEnginePairingCodePolicy.normalize("2345-6789-abcd"))
        assertEquals("23456789ABCD", TermuxEnginePairingCodePolicy.normalize("2345 6789 ABCD"))
        assertTrue(runCatching { TermuxEnginePairingCodePolicy.normalize("123") }.isFailure)
        assertTrue(runCatching { TermuxEnginePairingCodePolicy.normalize("000000000000") }.isFailure)
    }

    @Test
    fun pairingRequestNeverIncludesStoredToken() {
        val encoded = TermuxEnginePairingRequestEncoder.encode("request_12345678", "2345-6789-abcd")
        assertTrue(encoded.contains("\"operation\":\"PAIR\""))
        assertTrue(encoded.contains("\"pairingCode\":\"23456789ABCD\""))
        assertFalse(encoded.contains("\"token\""))
    }

    @Test
    fun requestEncoderExposesOnlyTypedOperations() {
        val encoded = TermuxEngineRequestEncoder.encode("request_12345678", TermuxEngineOperation.PING, token)
        assertTrue(encoded.contains("\"projectId\":\"localcut\""))
        assertTrue(encoded.contains("\"operation\":\"PING\""))
        assertTrue(encoded.endsWith("\n"))
    }

    @Test
    fun responseParserValidatesPairingProtocolAndToken() {
        val pairing = TermuxEngineResponseParser.parsePairing(
            MiniJson.parse(
                """{"ok":true,"protocolVersion":1,"token":"${"t".repeat(43)}"}""",
            ).asObject(),
        )
        assertEquals(1, pairing.protocolVersion)
        assertEquals("t".repeat(43), pairing.token)
        assertTrue(
            runCatching {
                TermuxEngineResponseParser.parsePairing(
                    MiniJson.parse("""{"ok":true,"protocolVersion":1,"token":"short"}""").asObject(),
                )
            }.isFailure,
        )
    }

    @Test
    fun responseParserValidatesProtocolProjectAndCapabilities() {
        val ping = TermuxEngineResponseParser.parsePing(
            MiniJson.parse("""{"ok":true,"protocolVersion":1,"status":"READY"}""").asObject(),
        )
        assertEquals("READY", ping.status)
        val status = TermuxEngineResponseParser.parseStatus(
            MiniJson.parse(
                """{"ok":true,"protocolVersion":1,"engineVersion":"0.2.0","projectId":"localcut","status":"READY","pid":123}""",
            ).asObject(),
        )
        assertEquals(123L, status.pid)
        val caps = TermuxEngineResponseParser.parseCapabilities(
            MiniJson.parse(
                """{"ok":true,"protocolVersion":1,"capabilities":["PING","GET_STATUS","GET_CAPABILITIES"]}""",
            ).asObject(),
        )
        assertTrue(caps.capabilities.containsAll(TermuxEngineProtocol.REQUIRED_CAPABILITIES))
        assertTrue(
            runCatching {
                TermuxEngineResponseParser.parseStatus(
                    MiniJson.parse(
                        """{"ok":true,"protocolVersion":1,"engineVersion":"0.2.0","projectId":"other","status":"READY","pid":123}""",
                    ).asObject(),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                TermuxEngineResponseParser.parseCapabilities(
                    MiniJson.parse("""{"ok":true,"protocolVersion":2,"capabilities":["PING"]}""").asObject(),
                )
            }.isFailure,
        )
    }
}
