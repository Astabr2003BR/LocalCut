package com.localcut.editor.mobile

import com.localcut.editor.bridge.agent.BridgeCommandExecutor
import com.localcut.editor.bridge.agent.BridgePlaybackPort
import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
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
import org.junit.Assert.assertTrue
import org.junit.Test

class CutDeleteAfterHandoffTest {
    @Test
    fun parserAcceptsSemanticCutDeleteAfterWithoutClipId() {
        val command = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=cut_delete_after&positionUs=2300000&returnTo=chatgpt",
        ).getOrThrow()

        assertEquals(BridgeCapability.TRIM_CLIP, command.capability)
        assertEquals(2_300_000L, command.resolveVisualClipAtPositionUs)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, command.returnTarget)
    }

    @Test
    fun resolverChoosesUniqueVisualClipContainingCutPoint() {
        val project = project(
            visualClips = listOf(
                Clip("visual-1", "video-asset", 0L, 4_640_000L, 0L, 4_640_000L),
            ),
        )
        val command = semanticCommand(2_300_000L)

        val payload = CutDeleteAfterResolver.resolvePayload(
            command = command,
            project = project,
            selectedClipId = null,
        ).getOrThrow()

        assertEquals("{\"clipId\":\"visual-1\",\"newEndUs\":2300000}", payload)
    }

    @Test
    fun resolverRejectsAmbiguousOverlappingVisualClips() {
        val project = project(
            visualClips = listOf(
                Clip("visual-1", "video-asset", 0L, 4_640_000L, 0L, 4_640_000L),
                Clip("visual-2", "video-asset", 1_000_000L, 3_000_000L, 0L, 3_000_000L),
            ),
        )

        val result = CutDeleteAfterResolver.resolvePayload(
            command = semanticCommand(2_300_000L),
            project = project,
            selectedClipId = null,
        )

        assertTrue(result.isFailure)
        assertEquals("CLIP_AT_POSITION_AMBIGUOUS", result.exceptionOrNull()?.message)
    }

    @Test
    fun selectedVisualClipDisambiguatesOverlapWhenItContainsCutPoint() {
        val project = project(
            visualClips = listOf(
                Clip("visual-1", "video-asset", 0L, 4_640_000L, 0L, 4_640_000L),
                Clip("visual-2", "video-asset", 1_000_000L, 3_000_000L, 0L, 3_000_000L),
            ),
        )

        val payload = CutDeleteAfterResolver.resolvePayload(
            command = semanticCommand(2_300_000L),
            project = project,
            selectedClipId = "visual-2",
        ).getOrThrow()

        assertEquals("{\"clipId\":\"visual-2\",\"newEndUs\":2300000}", payload)
    }

    @Test
    fun resolvedPayloadTrimsRightSideInOneUndoableEdit() {
        val project = project(
            visualClips = listOf(
                Clip("visual-1", "video-asset", 0L, 4_640_000L, 0L, 4_640_000L),
            ),
        )
        val session = session(project)
        val executor = BridgeCommandExecutor(session, FakePlayback())
        val payload = CutDeleteAfterResolver.resolvePayload(
            command = semanticCommand(2_300_000L),
            project = session.state.project,
            selectedClipId = null,
        ).getOrThrow()

        val result = executor.execute(
            BridgeCommandEnvelope(
                protocolVersion = 1,
                messageId = "cut-delete-after-1",
                kind = "command",
                deviceId = "local-android-handoff",
                projectId = project.id,
                expectedRevision = 5L,
                issuedAtEpochMs = 1_000L,
                expiresAtEpochMs = 61_000L,
                nonce = "nonce-cut-delete-after-1",
                capability = BridgeCapability.TRIM_CLIP,
                payloadJson = payload,
            ),
        )

        assertTrue(result.success)
        assertEquals(6L, session.state.revision)
        assertEquals(2_300_000L, session.state.project.findClip("visual-1")!!.durationUs)
    }

    private fun semanticCommand(positionUs: Long) = MobileHandoffCommand(
        capability = BridgeCapability.TRIM_CLIP,
        payloadJson = "{}",
        description = "Cortar e apagar a parte da direita",
        resolveVisualClipAtPositionUs = positionUs,
    )

    private fun project(visualClips: List<Clip>): Project {
        val videoAsset = Asset(
            id = "video-asset",
            type = AssetType.VIDEO,
            uri = "content://private/video",
            sourceDurationUs = 10_000_000L,
            sizeBytes = 100L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video-fp",
        )
        return Project.create("Semantic mobile cut", 1080, 1920, 30).copy(
            id = "project-semantic-cut",
            assets = listOf(videoAsset),
            tracks = listOf(
                Track(
                    id = "visual-track",
                    type = TrackType.VISUAL,
                    order = 0,
                    clips = visualClips,
                ),
            ),
        )
    }

    private fun session(project: Project): ProjectSession = ProjectSession(
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

    private class FakePlayback : BridgePlaybackPort {
        override val positionUs: Long = 0L
        override fun seekToUs(positionUs: Long): Result<Unit> = Result.success(Unit)
    }
}
