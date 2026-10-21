package com.localcut.editor.bridge.protocol

import com.localcut.editor.data.project.JArray
import com.localcut.editor.data.project.JBool
import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.JNumber
import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.JString
import com.localcut.editor.data.project.JValue
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asBoolean
import com.localcut.editor.data.project.asInt
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required

object BridgeCodec {
    const val PROTOCOL_VERSION = 1

    fun encodeCommand(command: BridgeCommandEnvelope): String = buildString {
        append('{')
        field("protocolVersion", command.protocolVersion.toString()); comma()
        field("messageId", jsonString(command.messageId)); comma()
        field("kind", jsonString(command.kind)); comma()
        field("deviceId", jsonString(command.deviceId)); comma()
        field("projectId", jsonString(command.projectId)); comma()
        if (command.expectedRevision != null) {
            field("expectedRevision", command.expectedRevision.toString()); comma()
        }
        field("issuedAtEpochMs", command.issuedAtEpochMs.toString()); comma()
        field("expiresAtEpochMs", command.expiresAtEpochMs.toString()); comma()
        field("nonce", jsonString(command.nonce)); comma()
        field("capability", jsonString(command.capability.wireName)); comma()
        field("payload", encodeValue(MiniJson.parse(command.payloadJson)))
        append('}')
    }

    fun decodeCommand(json: String): BridgeCommandEnvelope = decodeSafely {
        val root = MiniJson.parse(json).asObject()
        val protocolVersion = root.required("protocolVersion").asInt()
        if (protocolVersion != PROTOCOL_VERSION) {
            throw BridgeProtocolException(BridgeErrorCode.PROTOCOL_UNSUPPORTED, "unsupported protocolVersion=$protocolVersion")
        }
        val capabilityName = root.required("capability").asString()
        val capability = BridgeCapability.fromWireName(capabilityName)
            ?: throw BridgeProtocolException(BridgeErrorCode.CAPABILITY_NOT_ALLOWED, "capability not allowed: $capabilityName")
        val expectedRevision = root.optional("expectedRevision").nullableLong()
        if (capability.requiresRevision && expectedRevision == null) {
            throw BridgeProtocolException(BridgeErrorCode.INVALID_ARGUMENT, "expectedRevision required for ${capability.wireName}")
        }
        if (expectedRevision != null && expectedRevision < 0L) {
            throw BridgeProtocolException(BridgeErrorCode.INVALID_ARGUMENT, "expectedRevision must be non-negative")
        }
        val command = BridgeCommandEnvelope(
            protocolVersion = protocolVersion,
            messageId = root.required("messageId").asString().requiredNonBlank("messageId"),
            kind = root.required("kind").asString(),
            deviceId = root.required("deviceId").asString().requiredNonBlank("deviceId"),
            projectId = root.required("projectId").asString().requiredNonBlank("projectId"),
            expectedRevision = expectedRevision,
            issuedAtEpochMs = root.required("issuedAtEpochMs").asLong(),
            expiresAtEpochMs = root.required("expiresAtEpochMs").asLong(),
            nonce = root.required("nonce").asString().requiredNonBlank("nonce"),
            capability = capability,
            payloadJson = encodeValue(root.required("payload")),
        )
        if (command.kind != "command") {
            throw BridgeProtocolException(BridgeErrorCode.INVALID_ARGUMENT, "kind must be command")
        }
        if (command.issuedAtEpochMs < 0L || command.expiresAtEpochMs <= command.issuedAtEpochMs) {
            throw BridgeProtocolException(BridgeErrorCode.INVALID_ARGUMENT, "invalid command timestamps")
        }
        command
    }

    fun encodeResult(result: BridgeResultEnvelope): String = buildString {
        append('{')
        field("protocolVersion", PROTOCOL_VERSION.toString()); comma()
        field("messageId", jsonString(result.messageId)); comma()
        field("success", result.success.toString()); comma()
        field("code", result.code?.let { jsonString(it.wireName) } ?: "null"); comma()
        field("projectRevision", result.projectRevision?.toString() ?: "null"); comma()
        field("retryable", result.retryable.toString()); comma()
        field("result", encodeValue(MiniJson.parse(result.resultJson)))
        append('}')
    }

    fun decodeResult(json: String): BridgeResultEnvelope = decodeSafely {
        val root = MiniJson.parse(json).asObject()
        val protocolVersion = root.required("protocolVersion").asInt()
        if (protocolVersion != PROTOCOL_VERSION) {
            throw BridgeProtocolException(BridgeErrorCode.PROTOCOL_UNSUPPORTED, "unsupported protocolVersion=$protocolVersion")
        }
        val codeValue = root.optional("code")
        val code = when (codeValue) {
            null, JNull -> null
            else -> BridgeErrorCode.fromWireName(codeValue.asString())
                ?: throw BridgeProtocolException(BridgeErrorCode.INVALID_ARGUMENT, "unknown result code")
        }
        BridgeResultEnvelope(
            messageId = root.required("messageId").asString().requiredNonBlank("messageId"),
            success = root.required("success").asBoolean(),
            code = code,
            projectRevision = root.optional("projectRevision").nullableLong(),
            resultJson = encodeValue(root.required("result")),
            retryable = root.required("retryable").asBoolean(),
        )
    }

    private inline fun <T> decodeSafely(block: () -> T): T = try {
        block()
    } catch (error: BridgeProtocolException) {
        throw error
    } catch (error: Throwable) {
        throw BridgeProtocolException(
            BridgeErrorCode.INVALID_ARGUMENT,
            error.message ?: "invalid bridge JSON",
            error,
        )
    }

    private fun String.requiredNonBlank(field: String): String {
        if (isBlank()) throw BridgeProtocolException(BridgeErrorCode.INVALID_ARGUMENT, "$field must not be blank")
        return this
    }

    private fun JValue?.nullableLong(): Long? = when (this) {
        null, JNull -> null
        else -> asLong()
    }

    private fun encodeValue(value: JValue): String = when (value) {
        is JObject -> value.fields.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "${jsonString(key)}:${encodeValue(item)}"
        }
        is JArray -> value.values.joinToString(prefix = "[", postfix = "]") { encodeValue(it) }
        is JString -> jsonString(value.value)
        is JNumber -> value.raw
        is JBool -> value.value.toString()
        JNull -> "null"
    }

    private fun StringBuilder.field(name: String, encodedValue: String) {
        append(jsonString(name)); append(':'); append(encodedValue)
    }

    private fun StringBuilder.comma() {
        append(',')
    }
}
