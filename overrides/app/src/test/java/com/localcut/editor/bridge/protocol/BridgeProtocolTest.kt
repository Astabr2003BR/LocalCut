package com.localcut.editor.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeProtocolTest {
    @Test
    fun commandRoundTripPreservesProtocolFields() {
        val command = BridgeCommandEnvelope(
            protocolVersion = 1,
            messageId = "msg-1",
            kind = "command",
            deviceId = "device-1",
            projectId = "project-1",
            expectedRevision = 42L,
            issuedAtEpochMs = 1_000L,
            expiresAtEpochMs = 31_000L,
            nonce = "nonce-1",
            capability = BridgeCapability.SPLIT_CLIP,
            payloadJson = "{\"clipId\":\"clip-1\",\"positionUs\":1000000}",
        )

        val decoded = BridgeCodec.decodeCommand(BridgeCodec.encodeCommand(command))

        assertEquals(command, decoded)
    }

    @Test
    fun unknownProtocolVersionIsRejectedWithStableCode() {
        val json = validCommandJson().replace("\"protocolVersion\":1", "\"protocolVersion\":2")

        val error = runCatching { BridgeCodec.decodeCommand(json) }.exceptionOrNull() as BridgeProtocolException

        assertEquals(BridgeErrorCode.PROTOCOL_UNSUPPORTED, error.code)
    }

    @Test
    fun unknownCapabilityIsRejectedWithStableCode() {
        val json = validCommandJson().replace("\"split_clip\"", "\"launch_other_app\"")

        val error = runCatching { BridgeCodec.decodeCommand(json) }.exceptionOrNull() as BridgeProtocolException

        assertEquals(BridgeErrorCode.CAPABILITY_NOT_ALLOWED, error.code)
    }

    @Test
    fun missingRequiredFieldIsInvalidArgument() {
        val json = validCommandJson().replace(",\"nonce\":\"nonce-1\"", "")

        val error = runCatching { BridgeCodec.decodeCommand(json) }.exceptionOrNull() as BridgeProtocolException

        assertEquals(BridgeErrorCode.INVALID_ARGUMENT, error.code)
    }

    @Test
    fun readCapabilityMayOmitExpectedRevisionButWriteMayNot() {
        val readJson = validCommandJson(
            capability = "get_project_state",
            expectedRevisionField = "",
        )
        val read = BridgeCodec.decodeCommand(readJson)
        assertEquals(null, read.expectedRevision)

        val writeJson = validCommandJson(expectedRevisionField = "")
        val error = runCatching { BridgeCodec.decodeCommand(writeJson) }.exceptionOrNull() as BridgeProtocolException
        assertEquals(BridgeErrorCode.INVALID_ARGUMENT, error.code)
        assertTrue(BridgeCapability.SPLIT_CLIP.mutatesProject)
        assertFalse(BridgeCapability.GET_PROJECT_STATE.mutatesProject)
    }

    private fun validCommandJson(
        capability: String = "split_clip",
        expectedRevisionField: String = ",\"expectedRevision\":42",
    ): String = """{"protocolVersion":1,"messageId":"msg-1","kind":"command","deviceId":"device-1","projectId":"project-1"$expectedRevisionField,"issuedAtEpochMs":1000,"expiresAtEpochMs":31000,"nonce":"nonce-1","capability":"$capability","payload":{}}"""
}
