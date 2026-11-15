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
        val metadata = requireNotNull(fixture.jobs.get(firstJobId)!!.resultJson)
        assertTrue(metadata.contains("\"sourceProjectId\":\"project-1\""))
        assertTrue(metadata.contains("\"sourceRevision\":5"))

        val next = fixture.executor.execute(exportCommand("msg-export-2"))
        assertTrue(next.success)
        assertEquals(2, fixture.exportPort.startCalls)
    }

    @Test
    fun unsupportedSilentDestinationFailsExplicitlyWithoutPermissionEscalation() {
        val fixture = fixture()
        fixture.exportPort.startFailure = BridgeLocalInteractionRequiredException("Android 10+ required for unattended shared export; open LocalCut to export")

        val result = fixture.executor.execute(exportCommand("msg-export-legacy"))

        assertFalse(result.success)
        assertEquals(BridgeErrorCode.EXPORT_FAILED, result.code)
        assertTrue(result.resultJson.contains("Android 10+ required"))
        assertTrue(result.resultJson.contains("open LocalCut"))
    }

    @Test
    fun exportSamplesAreBoundToJobAndSourceRevisionAndNeverInStatus() {
        val fixture = fixture()
        val start = fixture.executor.execute(exportCommand("export-samples"))
        val id = jobId(start.resultJson)
        val sample = BridgeExportSample(0L, 1, 1, byteArrayOf(-1, -40, -1, -39))
        fixture.exportPort.listener!!.onCompletedWithReview("""{"destination":"LOCALCUT_MEDIASTORE"}""",
            BridgeExportInspection("{}", "a".repeat(64), listOf(sample)))
        val query = exportCommand("read-samples").copy(capability = BridgeCapability.GET_EXPORT_REVIEW_FRAMES,
            expectedRevision = null, payloadJson = """{"jobId":"$id","sourceRevision":5}""")
        val result = fixture.executor.execute(query)
        assertTrue(result.success)
        assertTrue(result.resultJson.contains("\"sourceRevision\":5"))
        assertTrue(result.resultJson.contains("\"base64\":"))
        assertFalse(requireNotNull(fixture.jobs.get(id)!!.resultJson).contains("base64"))
        assertFalse(fixture.executor.execute(query.copy(payloadJson = """{"jobId":"$id","sourceRevision":6}""")).success)
        assertFalse(fixture.executor.execute(query.copy(projectId = "other")).success)
        assertFalse(fixture.executor.execute(query.copy(payloadJson = """{"jobId":"$id","sourceRevision":5,"path":"bad"}""")).success)
        fixture.executor.execute(exportCommand("new-export"))
        assertFalse(fixture.executor.execute(query).success)
    }

    @Test
    fun exportEvidenceExpiresAndCannotBeReplacedByLateOldJob() {
        var now = 1000L
        val registry = BridgeJobRegistry { now }
        val first = (registry.reserveExport("one") as BridgeJobReservation.Started).job.jobId
        val sample = BridgeExportSample(0L, 1, 1, byteArrayOf(-1, -40, -1, -39))
        val review = BridgeExportInspection("{}", "a".repeat(64), listOf(sample))
        registry.succeedWithReview(first, "{}", "p", 5L, review)
        assertTrue(registry.reviewFrames(first, "p", 5L) != null)
        now += 600_001L
        assertTrue(registry.reviewFrames(first, "p", 5L) == null)
        val second = (registry.reserveExport("two") as BridgeJobReservation.Started).job.jobId
        registry.succeedWithReview(first, "{}", "p", 5L, review)
        assertTrue(registry.reviewFrames(first, "p", 5L) == null)
        registry.succeedWithReview(second, "{}", "p", 6L, review)
        assertTrue(registry.reviewFrames(second, "p", 6L) != null)
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
