package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.BridgeReviewHistory
import com.localcut.editor.bridge.agent.BridgeReviewProposal
import com.localcut.editor.bridge.agent.ReviewHistoryStatus
import com.localcut.editor.bridge.agent.ReviewHistoryStorage
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
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EditorAiApplyPersistenceTest {
    private class MemoryHistory : ReviewHistoryStorage {
        var content: String? = null
        override fun read(): String? = content
        override fun write(value: String): Boolean { content = value; return true }
    }
    private class TestStore : ProjectStore {
        var fail = false
        var saved: Project? = null
        var writes = 0
        override fun save(project: Project): Result<Unit> {
            writes++
            if (fail) return Result.failure(IllegalStateException("disk full"))
            saved = project
            return Result.success(Unit)
        }
        override fun load(): Result<Project> = saved?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("not saved"))
    }
    private data class Fixture(
        val session: ProjectSession, val saver: DebouncedProjectSaver,
        val store: TestStore, val history: BridgeReviewHistory,
        val proposalId: String, val source: EditorAiReviewSource, val draft: String,
    )
    private fun fixture(): Fixture {
        val store = TestStore()
        val asset = Asset("asset", AssetType.VIDEO, "content://test/video", 4_000_000L, 100L, "video.mp4", "video/mp4", "fp")
        val project = Project.create("Persist", 1080, 1920, 30).copy(id="p",assets=listOf(asset),
            tracks=listOf(Track("v", TrackType.VISUAL, 0,
                clips=listOf(Clip("clip",asset.id,0L,2_000_000L,0L,2_000_000L))),
                Track("a",TrackType.AUDIO,1)))
        val saver = DebouncedProjectSaver(store, object : DelayScheduler {
            override fun schedule(delayMs: Long, action: () -> Unit) = Cancellable { }
        }, 450L)
        val session = ProjectSession(project,
            preview=object : ProjectPreviewLoader { override fun load(project: Project) = Result.success(Unit) },
            autosave=saver, revisionClock=InMemoryProjectRevisionClock(mapOf("p" to 5L)))
        val history = BridgeReviewHistory(MemoryHistory())
        val id = UUID.randomUUID().toString()
        val source = EditorAiReviewSource("p",5L,UUID.randomUUID().toString(),"a".repeat(64))
        val draft = """{"projectId":"p","expectedRevision":5,"operations":[{"op":"patchClip","clipId":"clip","opacity":0.8}],"review":{"jobId":"${source.jobId}","sha256":"${source.sha256}","findings":"Ajuste identificado nos quadros."}}"""
        history.received(BridgeReviewProposal(id,source,draft))
        history.transition(id,ReviewHistoryStatus.LOADED)
        return Fixture(session,saver,store,history,id,source,draft)
    }
    @Test fun successfulApprovalSavesImmediatelyAndWritesDurableHistoryStatus() {
        val f=fixture()
        val original=f.session.state.project
        val gate=EditorAiApprovalGate()
        val approval=gate.prepare(f.draft,f.session.state,f.source).getOrThrow()
        assertNull(f.store.saved)
        assertEquals(original,f.session.state.project)
        val result=EditorAiApplyPersistence(f.history,f.saver::flushNow)
            .apply(gate,approval,f.draft,f.session,f.source,f.proposalId).getOrThrow()
        assertEquals(6L,result.revision)
        assertTrue(result.saved)
        assertEquals(1,f.store.writes)
        assertEquals(f.session.state.project,f.store.load().getOrThrow())
        assertEquals(ReviewHistoryStatus.SAVED_TO_DISK,f.history.view.value.entries.single().status)
        assertEquals(6L,f.history.view.value.entries.single().resultRevision)
        f.session.dispatch(EditorAction.Undo)
        assertEquals(original,f.session.state.project)
    }
    @Test fun failedSaveNeverClaimsDurableAndRetryRequiresSameRevision() {
        val f=fixture()
        f.store.fail=true
        val gate=EditorAiApprovalGate()
        val approval=gate.prepare(f.draft,f.session.state,f.source).getOrThrow()
        val service=EditorAiApplyPersistence(f.history,f.saver::flushNow)
        val result=service.apply(gate,approval,f.draft,f.session,f.source,f.proposalId).getOrThrow()
        assertEquals(6L,result.revision)
        assertFalse(result.saved)
        assertEquals(6L,f.session.state.revision)
        assertEquals(0.8,f.session.state.project.findClip("clip")!!.opacity,0.0)
        assertNull(f.store.saved)
        assertEquals(ReviewHistoryStatus.APPLIED_IN_EDITOR,f.history.view.value.entries.single().status)
        f.store.fail=false
        assertTrue(service.retry(f.proposalId,6L,f.session))
        assertEquals(f.session.state.project,f.store.saved)
        assertEquals(ReviewHistoryStatus.SAVED_TO_DISK,f.history.view.value.entries.single().status)
        assertFalse(service.retry(f.proposalId,5L,f.session))
        assertEquals(2,f.store.writes)
    }
    @Test fun rejectedApprovalNeverFlushesNorWritesHistoryAsApplied() {
        val f=fixture()
        val gate=EditorAiApprovalGate()
        val approval=gate.prepare(f.draft,f.session.state,f.source).getOrThrow()
        val result=EditorAiApplyPersistence(f.history,f.saver::flushNow)
            .apply(gate,approval,f.draft,f.session,f.source.copy(jobId="changed"),f.proposalId)
        assertTrue(result.isFailure)
        assertEquals(0,f.store.writes)
        assertEquals(5L,f.session.state.revision)
        assertEquals(ReviewHistoryStatus.LOADED,f.history.view.value.entries.single().status)
    }
    @Test fun thrownPersistenceCallbackDoesNotLoseAlreadyAppliedEditorChange() {
        val f=fixture()
        val gate=EditorAiApprovalGate()
        val approval=gate.prepare(f.draft,f.session.state,f.source).getOrThrow()
        val result=EditorAiApplyPersistence(f.history) { throw IllegalStateException("storage unavailable") }
            .apply(gate,approval,f.draft,f.session,f.source,f.proposalId).getOrThrow()
        assertFalse(result.saved)
        assertEquals(6L,f.session.state.revision)
        assertEquals(ReviewHistoryStatus.APPLIED_IN_EDITOR,f.history.view.value.entries.single().status)
    }
}
