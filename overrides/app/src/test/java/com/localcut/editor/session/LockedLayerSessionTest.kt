package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedLayerSessionTest {
    private class ImmediatePreview : ProjectPreviewLoader {
        override fun load(project: Project): Result<Unit> = Result.success(Unit)
    }

    private class NoopStore : ProjectStore {
        override fun save(project: Project): Result<Unit> = Result.success(Unit)
        override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
    }

    private class NoopScheduler : DelayScheduler {
        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable { }
    }

    private fun project(locked: Boolean): Project {
        val asset = Asset("a1", AssetType.VIDEO, "content://a1", 10_000_000, 1, "video.mp4", "video/mp4", "fp")
        return Project.create("Locked", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    "visual-1",
                    TrackType.VISUAL,
                    0,
                    clips = listOf(Clip("c1", "a1", 0, 2_000_000, 0, 2_000_000)),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            compositionGraph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(
                        id = "layer-c1",
                        kind = CompositionNodeKind.LAYER,
                        clipId = "c1",
                        locked = locked,
                    ),
                ),
            ),
        )
    }

    private fun session(locked: Boolean): ProjectSession {
        val store = NoopStore()
        return ProjectSession(
            initialProject = project(locked),
            preview = ImmediatePreview(),
            autosave = DebouncedProjectSaver(store, NoopScheduler(), 300),
        )
    }

    @Test
    fun lockedClipRejectsSelectedEditsWithoutMutatingProjectOrHistory() {
        val session = session(locked = true)
        session.dispatch(EditorAction.SelectClip("c1"))
        val baselineProject = session.state.project
        val baselineRevision = session.state.revision

        val actions = listOf<EditorAction>(
            EditorAction.MoveSelected(500_000L),
            EditorAction.SetSelectedTransform(TransformValue(x = 12.0)),
            EditorAction.SetSelectedVolume(0.5),
            EditorAction.SetSelectedTimeMap(TimeMap(baseSpeed = 2.0)),
            EditorAction.TrimSelected(100_000L, 1_900_000L),
            EditorAction.TrimSelectedLeftEdge(100_000L),
            EditorAction.TrimSelectedRightEdge(1_900_000L),
            EditorAction.SplitSelected(1_000_000L),
            EditorAction.DuplicateSelected,
            EditorAction.DeleteSelected,
        )

        actions.forEach { action ->
            session.dispatch(action)
            assertEquals(baselineProject, session.state.project)
            assertEquals(baselineRevision, session.state.revision)
            assertFalse(session.state.canUndo)
            assertTrue(session.state.selectedClipId == "c1")
            assertNotNull(session.state.lastError)
            assertTrue(session.state.lastError!!.contains("locked", ignoreCase = true))
        }
    }

    @Test
    fun unlockedClipKeepsNormalEditingPath() {
        val session = session(locked = false)
        session.dispatch(EditorAction.SelectClip("c1"))

        session.dispatch(EditorAction.MoveSelected(500_000L))

        assertEquals(500_000L, session.state.project.findClip("c1")!!.startUs)
        assertTrue(session.state.canUndo)
        assertEquals(null, session.state.lastError)
    }
}
