package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.Cancellable
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.DelayScheduler
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeExportCommandTest {
    @Test
    fun repeatedExportMessageStartsRendererOnceAndReturnsSameJob() {
        val fixture = fixture()

        val first = fixture.executor.execute(exportCommand("msg-export-1"))
        val retry = fixture.executor.execute(exportCommand("msg-export-1"))

        assertTrue(first.success)
        assertTrue(retry.success)
        assertEquals(1, fixture.exportPort.startCalls)
        assertEquals(jobId(first.resultJson), jobId(retry.resultJson))
    }

    @Test
    fun secondExportMessageIsBusyUntilFirstCompletes() {
        val fixture = fixture()
        fixture.executor.execute(exportCommand("msg-export-1"))

        val busy = fixture.executor.execute(exportCommand("msg-export-2"))

        assertFalse(busy.success)
        assertEquals(BridgeErrorCode.EXPORT_BUSY, busy.code)
        assertEquals(1, fixture.exportPort.startCalls)
    }

    @Test
    fun exportEventsUpdateJobAndReleaseSlot() {
        val fixture = fixture()
        val started = fixture.executor.execute(exportCommand("msg-export-1"))
        val firstJobId = jobId(started.resultJson)

        fixture.exportPort.listener!!.onStarted()
        fixture.exportPort.listener!!.onProgress(55)
        assertEquals(BridgeJobState.RUNNING, fixture.jobs.get(firstJobId)!!.state)
        assertEquals(55, fixture.jobs.get(firstJobId)!!.progressPercent)

        fixture.exportPort.listener!!.onCompleted("{\"destination\":\"LOCALCUT_MEDIASTORE\"}")
        assertEquals(BridgeJobState.SUCCEEDED, fixture.jobs.get(firstJobId)!!.state)

        val next = fixture.executor.execute(exportCommand("msg-export-2"))
        assertTrue(next.success)
        assertEquals(2, fixture.exportPort.startCalls)
    }

    @Test
    fun unsupportedSilentDestinationRequiresLocalInteraction() {
        val fixture = fixture()
        fixture.exportPort.startFailure = BridgeLocalInteractionRequiredException("Android 10+ required for unattended shared export")

        val result = fixture.executor.execute(exportCommand("msg-export-legacy"))

        assertFalse(result.success)
        assertEquals(BridgeErrorCode.LOCAL_INTERACTION_REQUIRED, result.code)
    }

    private data class Fixture(
        val executor: BridgeCommandExecutor,
        val exportPort: FakeExportPort,
        val jobs: BridgeJobRegistry,
    )

    private fun fixture(): Fixture {
        val asset = Asset("asset-1", AssetType.VIDEO, "content://private/video", 4_000_000L, 100L, "video.mp4", "video/mp4", "fp")
        val project = Project.create("Export", 1080, 1920, 30).copy(
            id = "project-1",
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(Clip("clip-1", asset.id, 0L, 2_000_000L, 0L, 2_000_000L))),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
        val session = ProjectSession(
            initialProject = project,
            preview = object : ProjectPreviewLoader { override fun load(project: Project): Result<Unit> = Result.success(Unit) },
            autosave = DebouncedProjectSaver(
                store = object : ProjectStore {
                    override fun save(project: Project): Result<Unit> = Result.success(Unit)
                    override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
                },
                scheduler = object : DelayScheduler { override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable { } },
                delayMs = 300L,
            ),
            revisionClock = InMemoryProjectRevisionClock(mapOf(project.id to 5L)),
        )
        val playback = object : BridgePlaybackPort {
            override val positionUs: Long = 0L
            override fun seekToUs(positionUs: Long): Result<Unit> = Result.success(Unit)
        }
        val jobs = BridgeJobRegistry()
        val exportPort = FakeExportPort()
        return Fixture(
            executor = BridgeCommandExecutor(session, playback, jobs, exportPort),
            exportPort = exportPort,
            jobs = jobs,
        )
    }

    private class FakeExportPort : BridgeExportPort {
        var startCalls = 0
        var listener: BridgeExportListener? = null
        var startFailure: Throwable? = null

        override fun start(project: Project, listener: BridgeExportListener): Result<Unit> {
            startCalls += 1
            startFailure?.let { return Result.failure(it) }
            this.listener = listener
            return Result.success(Unit)
        }

        override fun cancel(): Result<Unit> = Result.success(Unit)
    }

    private fun exportCommand(messageId: String) = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = messageId,
        kind = "command",
        deviceId = "device-1",
        projectId = "project-1",
        expectedRevision = 5L,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 31_000L,
        nonce = "nonce-$messageId",
        capability = BridgeCapability.EXPORT_PROJECT,
        payloadJson = "{}",
    )

    private fun jobId(json: String): String = Regex("\\\"jobId\\\":\\\"([^\\\"]+)\\\"")
        .find(json)?.groupValues?.get(1) ?: error("jobId missing: $json")
}
