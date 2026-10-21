package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import com.localcut.editor.session.Cancellable
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.DelayScheduler
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeAdvancedEditCommandsTest {
    @Test
    fun setKeyframesUsesProjectSessionAndAdvancesRevision() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.SET_KEYFRAMES,
                payload = """{"clipId":"clip-1","keyframes":[{"offsetUs":0,"x":0,"y":0,"scale":1,"rotationDeg":0,"opacity":1},{"offsetUs":1000000,"x":120,"y":-40,"scale":1.2,"rotationDeg":8,"opacity":0.7}]}""",
            )
        )

        assertTrue(result.success)
        assertEquals(6L, result.projectRevision)
        val keyframes = fixture.session.state.project.findClip("clip-1")!!.keyframes
        assertEquals(2, keyframes.size)
        assertEquals(1_000_000L, keyframes[1].offsetUs)
        assertEquals(120.0, keyframes[1].value.x, 0.0)
        assertEquals(0.7, keyframes[1].value.opacity, 0.0)
    }

    @Test
    fun addTextCreatesUndoableOverlay() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.ADD_TEXT,
                payload = """{"id":"text-1","startUs":250000,"durationUs":1500000,"text":"Anime","fontSizePx":72,"fillArgb":-1,"x":20,"y":30,"scale":1,"rotationDeg":0,"opacity":0.9,"zIndex":3}""",
            )
        )

        assertTrue(result.success)
        assertEquals(6L, result.projectRevision)
        val overlay = fixture.session.state.project.textOverlays.single()
        assertEquals("Anime", overlay.text)
        assertEquals(72.0, overlay.style.fontSizePx, 0.0)
        assertEquals(3, overlay.zIndex)
        assertTrue(fixture.session.state.canUndo)
    }

    @Test
    fun addFadeTransitionCreatesValidatedTransition() {
        val fixture = fixture()
        val result = fixture.executor.execute(
            command(
                BridgeCapability.ADD_TRANSITION,
                payload = """{"id":"transition-1","fromClipId":"clip-1","toClipId":"clip-2","durationUs":500000,"kind":"FADE_TO_BLACK"}""",
            )
        )

        assertTrue(result.success)
        assertEquals(6L, result.projectRevision)
        val transition = fixture.session.state.project.transitions.single()
        assertEquals(TransitionKind.FADE_TO_BLACK, transition.kind)
        assertEquals("clip-1", transition.fromClipId)
        assertEquals("clip-2", transition.toClipId)
    }

    private data class Fixture(
        val session: ProjectSession,
        val executor: BridgeCommandExecutor,
    )

    private fun fixture(): Fixture {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://private/video",
            sourceDurationUs = 8_000_000L,
            sizeBytes = 100L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp-1",
        )
        val project = Project.create("Bridge advanced", 1080, 1920, 30).copy(
            id = "project-1",
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    "visual-1",
                    TrackType.VISUAL,
                    0,
                    clips = listOf(
                        Clip("clip-1", asset.id, 0L, 2_000_000L, 0L, 2_000_000L),
                        Clip("clip-2", asset.id, 2_000_000L, 2_000_000L, 2_000_000L, 4_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
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
        return Fixture(session, BridgeCommandExecutor(session, playback))
    }

    private fun command(
        capability: BridgeCapability,
        payload: String,
    ): BridgeCommandEnvelope = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = "msg-${capability.wireName}",
        kind = "command",
        deviceId = "device-1",
        projectId = "project-1",
        expectedRevision = 5L,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 31_000L,
        nonce = "nonce-${capability.wireName}",
        capability = capability,
        payloadJson = payload,
    )
}
