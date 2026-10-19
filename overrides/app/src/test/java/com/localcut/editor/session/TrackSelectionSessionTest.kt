package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackSelectionSessionTest {
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

    private fun project(): Project {
        val asset = Asset("v", AssetType.VIDEO, "content://v", 5_000_000, 1, "v.mp4", "video/mp4", "v")
        return Project.create("tracks", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-main",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(Clip("main-clip", "v", 0, 1_000_000, 0, 1_000_000)),
                ),
                Track(
                    id = "visual-overlay",
                    type = TrackType.VISUAL,
                    order = 1,
                    collisionPolicy = TrackCollisionPolicy.OVERLAY,
                    clips = listOf(Clip("overlay-clip", "v", 0, 1_000_000, 1_000_000, 2_000_000)),
                ),
                Track("audio-1", TrackType.AUDIO, order = 2),
            ),
        )
    }

    private fun session(): ProjectSession {
        val store = NoopStore()
        return ProjectSession(
            initialProject = project(),
            preview = ImmediatePreview(),
            autosave = DebouncedProjectSaver(store, NoopScheduler(), 300),
        )
    }

    @Test
    fun selectingTrackStoresTrackAndClearsClipSelection() {
        val session = session()
        session.dispatch(EditorAction.SelectClip("main-clip"))
        assertEquals("main-clip", session.state.selectedClipId)
        assertEquals("visual-main", session.state.selectedTrackId)

        session.dispatch(EditorAction.SelectTrack("visual-overlay"))

        assertEquals("visual-overlay", session.state.selectedTrackId)
        assertNull(session.state.selectedClipId)
        assertNull(session.state.lastError)
    }

    @Test
    fun selectingClipAlsoSelectsOwningTrack() {
        val session = session()

        session.dispatch(EditorAction.SelectClip("overlay-clip"))

        assertEquals("overlay-clip", session.state.selectedClipId)
        assertEquals("visual-overlay", session.state.selectedTrackId)
        assertNull(session.state.lastError)
    }

    @Test
    fun clearingClipKeepsCurrentTrackSelection() {
        val session = session()
        session.dispatch(EditorAction.SelectTrack("visual-overlay"))

        session.dispatch(EditorAction.SelectClip(null))

        assertEquals("visual-overlay", session.state.selectedTrackId)
        assertNull(session.state.selectedClipId)
    }

    @Test
    fun missingTrackIsRejectedWithoutStaleSelection() {
        val session = session()
        session.dispatch(EditorAction.SelectTrack("visual-main"))

        session.dispatch(EditorAction.SelectTrack("missing"))

        assertNull(session.state.selectedTrackId)
        assertNull(session.state.selectedClipId)
        assertEquals("track missing not found", session.state.lastError)
    }
}
