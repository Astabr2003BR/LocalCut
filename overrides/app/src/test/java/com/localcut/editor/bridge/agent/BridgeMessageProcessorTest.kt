package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCodec
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Project
import com.localcut.editor.session.Cancellable
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.DelayScheduler
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMessageProcessorTest {
    @Test
    fun validReadCommandExecutesAndProducesProtocolResult() {
        val fixture = fixture()
        val command = command("msg-1", "nonce-1", expiresAt = 31_000L)

        val encoded = fixture.processor.process(BridgeCodec.encodeCommand(command), nowEpochMs = 1_000L)
        val result = BridgeCodec.decodeResult(requireNotNull(encoded))

        assertTrue(result.success)
        assertEquals("msg-1", result.messageId)
        assertEquals(5L, result.projectRevision)
    }

    @Test
    fun repeatedCompletedMessageReturnsCachedResultWithoutSecondExecution() {
        val fixture = fixture()
        val command = command("msg-1", "nonce-1", expiresAt = 31_000L)
        val encoded = BridgeCodec.encodeCommand(command)

        val first = fixture.processor.process(encoded, nowEpochMs = 1_000L)
        val second = fixture.processor.process(encoded, nowEpochMs = 2_000L)

        assertEquals(first, second)
    }

    @Test
    fun expiredAndReusedNonceAreRejectedBeforeExecutorMutation() {
        val fixture = fixture()
        val expired = command("expired", "nonce-expired", expiresAt = 1_500L)
        val expiredResult = BridgeCodec.decodeResult(
            requireNotNull(fixture.processor.process(BridgeCodec.encodeCommand(expired), nowEpochMs = 1_501L)),
        )
        assertEquals(BridgeErrorCode.COMMAND_EXPIRED, expiredResult.code)

        val first = command("first", "shared-nonce", expiresAt = 31_000L)
        fixture.processor.process(BridgeCodec.encodeCommand(first), nowEpochMs = 2_000L)
        val replay = command("second", "shared-nonce", expiresAt = 31_000L)
        val replayResult = BridgeCodec.decodeResult(
            requireNotNull(fixture.processor.process(BridgeCodec.encodeCommand(replay), nowEpochMs = 2_001L)),
        )
        assertEquals(BridgeErrorCode.REPLAY_REJECTED, replayResult.code)
    }

    @Test
    fun commandForDifferentDeviceIsRejected() {
        val fixture = fixture()
        val foreign = command("msg-foreign", "nonce-f", expiresAt = 31_000L).copy(deviceId = "other-device")

        val result = BridgeCodec.decodeResult(
            requireNotNull(fixture.processor.process(BridgeCodec.encodeCommand(foreign), nowEpochMs = 1_000L)),
        )

        assertEquals(BridgeErrorCode.AUTH_REQUIRED, result.code)
    }

    @Test
    fun malformedPayloadWithoutUsableMessageIdIsDropped() {
        val fixture = fixture()
        assertNull(fixture.processor.process("not-json", nowEpochMs = 1_000L))
    }

    private data class Fixture(val processor: BridgeMessageProcessor)

    private fun fixture(): Fixture {
        val project = Project.create("Bridge", 1080, 1920, 30).copy(id = "project-1")
        val session = ProjectSession(
            initialProject = project,
            preview = object : ProjectPreviewLoader {
                override fun load(project: Project): Result<Unit> = Result.success(Unit)
            },
            autosave = DebouncedProjectSaver(
                store = object : ProjectStore {
                    override fun save(project: Project): Result<Unit> = Result.success(Unit)
                    override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
                },
                scheduler = object : DelayScheduler {
                    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable { }
                },
                delayMs = 300L,
            ),
            revisionClock = InMemoryProjectRevisionClock(mapOf(project.id to 5L)),
        )
        val playback = object : BridgePlaybackPort {
            override val positionUs: Long = 0L
            override fun seekToUs(positionUs: Long): Result<Unit> = Result.success(Unit)
        }
        val executor = BridgeCommandExecutor(session, playback)
        return Fixture(
            BridgeMessageProcessor(
                deviceId = "device-1",
                executor = executor,
            ),
        )
    }

    private fun command(messageId: String, nonce: String, expiresAt: Long) = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = messageId,
        kind = "command",
        deviceId = "device-1",
        projectId = "project-1",
        expectedRevision = null,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = expiresAt,
        nonce = nonce,
        capability = BridgeCapability.GET_PROJECT_STATE,
        payloadJson = "{}",
    )
}
