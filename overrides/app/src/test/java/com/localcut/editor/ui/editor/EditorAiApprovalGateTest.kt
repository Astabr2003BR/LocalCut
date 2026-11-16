package com.localcut.editor.ui.editor

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

class EditorAiApprovalGateTest {
    private fun fixture(still: Boolean = false): ProjectSession {
        val asset = Asset("asset", AssetType.VIDEO, "content://private/video", 4_000_000L, 100L, "video.mp4", "video/mp4", "fp")
        val original = if (still) asset.copy(type = AssetType.IMAGE) else asset
        val p = Project.create("Editor IA", 1080, 1920, 30).copy(id = "p", assets = if (still) listOf(original, original.copy(id = "new")) else listOf(asset),
            tracks = listOf(Track("v", TrackType.VISUAL, 0, clips = listOf(Clip("clip", asset.id, 0L, 2_000_000L, 0L, 2_000_000L))),
                Track("a", TrackType.AUDIO, 1)))
        return ProjectSession(p,
            preview = object : ProjectPreviewLoader { override fun load(project: Project) = Result.success(Unit) },
            autosave = DebouncedProjectSaver(
                store = object : ProjectStore {
                    override fun save(project: Project) = Result.success(Unit)
                    override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
                },
                scheduler = object : DelayScheduler { override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable {} },
                delayMs = 300L),
            revisionClock = InMemoryProjectRevisionClock(mapOf(p.id to 5L)))
    }
    private fun plan(revision: Long = 5L, operation: String = """{"op":"patchClip","clipId":"clip","opacity":0.8}""") =
        """{"projectId":"p","expectedRevision":$revision,"operations":[$operation]}"""

    @Test fun stillReplacementRequiresApprovalAndPreservesOriginal() {
        val s = fixture(still = true); val original = s.state.project; val gate = EditorAiApprovalGate()
        val draft = plan(operation = """{"op":"replaceStillImage","clipId":"clip","expectedAssetId":"asset","replacementAssetId":"new"}""")
        val approval = gate.prepare(draft, s.state).getOrThrow()
        assertEquals(original, s.state.project)
        assertTrue(approval.summary.contains("new"))
        gate.apply(approval, draft, s).getOrThrow()
        assertEquals("new", s.state.project.findClip("clip")!!.assetId)
        assertEquals(original.assets, s.state.project.assets)
        s.dispatch(EditorAction.Undo)
        assertEquals(original, s.state.project)
        assertTrue(gate.prepare(draft, fixture().state).isFailure)
    }

    @Test fun validateIsReadOnlyApplyOnceAndUndoRestoresExactly() {
        val s = fixture(); val original = s.state.project; val gate = EditorAiApprovalGate(); val draft = plan()
        val approval = gate.prepare(draft, s.state).getOrThrow()
        assertEquals(original, s.state.project)
        assertEquals(5L, s.state.revision)
        assertEquals(6L, gate.apply(approval, draft, s).getOrThrow())
        assertEquals(0.8, s.state.project.findClip("clip")!!.opacity, 0.0)
        assertEquals(original.assets, s.state.project.assets)
        assertTrue(gate.apply(approval, draft, s).isFailure)
        s.dispatch(EditorAction.Undo)
        assertEquals(original, s.state.project)
    }
    @Test fun changedDraftCancelAndNewGateRejectPriorApproval() {
        val s = fixture(); val gate = EditorAiApprovalGate(); val draft = plan()
        val first = gate.prepare(draft, s.state).getOrThrow()
        assertTrue(gate.apply(first, draft + " ", s).isFailure)
        val second = gate.prepare(draft, s.state).getOrThrow()
        gate.cancel()
        assertTrue(gate.apply(second, draft, s).isFailure)
        val third = gate.prepare(draft, s.state).getOrThrow()
        assertTrue(EditorAiApprovalGate().apply(third, draft, s).isFailure)
        assertEquals(5L, s.state.revision)
    }
    @Test fun staleRevisionAndForeignProjectFailClosed() {
        val s = fixture(); val gate = EditorAiApprovalGate(); val draft = plan()
        val approval = gate.prepare(draft, s.state).getOrThrow()
        val other = EditorAiApprovalGate(); val change = plan(operation = """{"op":"patchClip","clipId":"clip","scale":1.2}""")
        other.apply(other.prepare(change, s.state).getOrThrow(), change, s).getOrThrow()
        assertTrue(gate.apply(approval, draft, s).isFailure)
        assertTrue(gate.prepare(draft, s.state).isFailure)
        assertTrue(gate.prepare(plan(6).replace("\"p\"", "\"other\""), s.state).isFailure)
        assertEquals(1.2, s.state.project.findClip("clip")!!.transform.scale, 0.0)
    }
    @Test fun rejectsDeleteTimingAudioUnknownEmptyOversizeAndInvalidAtomicBatch() {
        val s = fixture(); val gate = EditorAiApprovalGate(); val original = s.state.project
        for (op in listOf("""{"op":"deleteClip","clipId":"clip"}""", """{"op":"patchClip","clipId":"clip","volume":0}""",
            """{"op":"patchClip","clipId":"clip","startUs":10}""", """{"op":"patchClip","clipId":"clip"}""",
            """{"op":"patchClip","clipId":"missing","opacity":0.5}""", """{"op":"patchClip","clipId":"clip","opacity":0.5,"path":"bad"}""")) {
            assertTrue(gate.prepare(plan(operation = op), s.state).isFailure)
        }
        assertTrue(gate.prepare("", s.state).isFailure)
        assertTrue(gate.prepare("é".repeat(4097), s.state).isFailure)
        assertTrue(gate.prepare(plan(operation = """{"op":"patchClip","clipId":"clip","opacity":0.8},{"op":"patchClip","clipId":"missing","opacity":0.5}"""), s.state).isFailure)
        assertEquals(original, s.state.project)
        assertEquals(5L, s.state.revision)
    }
    @Test fun exportReviewCorrectionBindsExactJobHashAndRevisionAndRequiresApproval() {
        val s = fixture(); val original = s.state.project; val gate = EditorAiApprovalGate()
        val source = EditorAiReviewSource("p", 5L, "export-1", "a".repeat(64))
        val reviewDraft = plan().dropLast(1) + ""","review":{"jobId":"export-1","sha256":"${source.sha256}","findings":"Quadros amostrados: ajustar opacidade."}}"""
        assertTrue(gate.prepare(reviewDraft, s.state).isFailure)
        assertTrue(gate.prepare(reviewDraft, s.state, source.copy(jobId = "other")).isFailure)
        assertTrue(gate.prepare(reviewDraft, s.state, source.copy(sha256 = "b".repeat(64))).isFailure)
        assertTrue(gate.prepare(reviewDraft, s.state, source.copy(revision = 4L)).isFailure)
        assertTrue(gate.prepare(reviewDraft.replace("Quadros amostrados: ajustar opacidade.", ""), s.state, source).isFailure)
        assertTrue(gate.prepare(reviewDraft.replace("Quadros amostrados: ajustar opacidade.", "x".repeat(1001)), s.state, source).isFailure)
        val first = gate.prepare(reviewDraft, s.state, source).getOrThrow()
        assertEquals(source, first.reviewSource)
        assertEquals("Quadros amostrados: ajustar opacidade.", first.reviewFindings)
        assertEquals(original, s.state.project)
        assertTrue(gate.apply(first, reviewDraft, s, source.copy(jobId = "new-export")).isFailure)
        assertEquals(original, s.state.project)
        assertTrue(gate.apply(first, reviewDraft, s, source).isFailure)
        val second = gate.prepare(reviewDraft, s.state, source).getOrThrow()
        assertEquals(6L, gate.apply(second, reviewDraft, s, source).getOrThrow())
        assertEquals(0.8, s.state.project.findClip("clip")!!.opacity, 0.0)
        assertEquals(original.assets, s.state.project.assets)
        assertTrue(gate.prepare(reviewDraft, s.state, source).isFailure)
        s.dispatch(EditorAction.Undo)
        assertEquals(original, s.state.project)
    }
    @Test fun contextContainsExactRoutingAndNoPrivateMediaPath() {
        val s = fixture()
        val context = EditorAiApprovalGate.context(s.state)
        assertTrue(context.contains("\"projectId\":\"p\""))
        assertTrue(context.contains("\"expectedRevision\":5"))
        assertTrue(context.contains("\"id\":\"clip\""))
        assertFalse(context.contains("content://"))
    }
}
