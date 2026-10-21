package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCodec
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.bridge.protocol.BridgeProtocolException
import com.localcut.editor.bridge.protocol.BridgeResultEnvelope
import com.localcut.editor.bridge.security.ReplayDecision
import com.localcut.editor.bridge.security.ReplayGuard

class BridgeMessageProcessor(
    private val deviceId: String,
    private val executor: BridgeCommandExecutor,
    private val replayGuard: ReplayGuard = ReplayGuard(),
    private val onExecuted: () -> Unit = {},
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
    }

    @Synchronized
    fun process(text: String, nowEpochMs: Long = System.currentTimeMillis()): String? {
        val messageId = extractMessageId(text)
        val command = try {
            BridgeCodec.decodeCommand(text)
        } catch (error: BridgeProtocolException) {
            return messageId?.let { id ->
                BridgeCodec.encodeResult(errorResult(id, error.code, retryable = false))
            }
        } catch (_: Throwable) {
            return messageId?.let { id ->
                BridgeCodec.encodeResult(errorResult(id, BridgeErrorCode.INVALID_ARGUMENT, retryable = false))
            }
        }

        if (command.deviceId != deviceId) {
            return BridgeCodec.encodeResult(
                errorResult(command.messageId, BridgeErrorCode.AUTH_REQUIRED, retryable = false),
            )
        }

        return when (val decision = replayGuard.validateAndReserve(
            messageId = command.messageId,
            nonce = command.nonce,
            expiresAtEpochMs = command.expiresAtEpochMs,
            nowEpochMs = nowEpochMs,
        )) {
            ReplayDecision.Reserved -> {
                val result = runCatching { executor.execute(command) }
                    .getOrElse {
                        errorResult(command.messageId, BridgeErrorCode.INTERNAL_ERROR, retryable = true)
                    }
                runCatching(onExecuted)
                replayGuard.complete(command.messageId, result)
                BridgeCodec.encodeResult(result)
            }

            ReplayDecision.DuplicatePending -> BridgeCodec.encodeResult(
                errorResult(command.messageId, BridgeErrorCode.RATE_LIMITED, retryable = true),
            )

            is ReplayDecision.Completed -> BridgeCodec.encodeResult(decision.result)

            is ReplayDecision.Rejected -> BridgeCodec.encodeResult(
                errorResult(
                    command.messageId,
                    decision.code,
                    retryable = decision.code == BridgeErrorCode.RATE_LIMITED,
                ),
            )
        }
    }

    private fun errorResult(
        messageId: String,
        code: BridgeErrorCode,
        retryable: Boolean,
    ): BridgeResultEnvelope = BridgeResultEnvelope(
        messageId = messageId,
        success = false,
        code = code,
        projectRevision = null,
        resultJson = "{}",
        retryable = retryable,
    )

    private fun extractMessageId(text: String): String? = MESSAGE_ID_REGEX
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }

    private companion object {
        val MESSAGE_ID_REGEX = Regex("\\\"messageId\\\"\\s*:\\s*\\\"([^\\\"]{1,256})\\\"")
    }
}
