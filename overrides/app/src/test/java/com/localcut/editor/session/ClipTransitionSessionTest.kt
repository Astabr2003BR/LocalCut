package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTransitionSessionTest {
    @Test
    fun addUpdateDeleteTransitionFlowsThroughUndoRedo() {
        val session = newSession(sequentialProject())
        val transition = ClipTransition(
            id = "transition-a-b",
            fromClipId = "a",
            toClipId = "b",
            durationUs = 500_000L,
            kind = TransitionKind.FADE_TO_BLACK,
        )

        session.dispatch(EditorAction.AddClipTransition(transition))
        assertEquals(transition, session.state.project.transitions.single())

        val updated = transition.copy(durationUs = 750_000L)
        session.dispatch(EditorAction.UpdateClipTransition(updated))
        assertEquals(updated, session.state.project.transitions.single())

        session.dispatch(EditorAction.DeleteClipTransition(updated.id))
        assertTrue(session.state.project.transitions.isEmpty())

        session.dispatch(EditorAction.Undo)
        assertEquals(updated, session.state.project.transitions.single())
        session.dispatch(EditorAction.Undo)
        assertEquals(transition, session.state.project.transitions.single())
        session.dispatch(EditorAction.Undo)
        assertTrue(session.state.project.transitions.isEmpty())

        session.dispatch(EditorAction.Redo)
        session.dispatch(EditorAction.Redo)
        session.dispatch(EditorAction.Redo)
        assertTrue(session.state.project.transitions.isEmpty())
    }

    private fun sequentialProject(): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 6_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        return Project.create("Transitions", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip("a", asset.id, 0L, 3_000_000L, 0L, 3_000_000L),
                        Clip("b", asset.id, 3_000_000L, 3_000_000L, 3_000_000L, 6_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }

    private fun newSession(project: Project): ProjectSession = ProjectSession(
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
    )
}
