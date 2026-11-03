package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
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

class BridgeAnimationCommandsTest {
    @Test
    fun animateCharacterWalkCompilesAndAppliesRevisionProtectedMotion() {
        val fx = fixture()
        val result = fx.executor.execute(
            command(
                BridgeCapability.ANIMATE_CHARACTER,
                """{"action":"WALK","bodyClipId":"body","startOffsetUs":0,"durationUs":1500000,"direction":1,"distancePx":260,"intensity":0.8}""",
            ),
        )

        assertTrue(result.success)
        assertEquals(6L, result.projectRevision)
        val body = fx.session.state.project.findClip("body")!!
        assertTrue(body.propertyAnimations.any { it.propertyId == "transform.x" })
        assertTrue(body.propertyAnimations.any { it.propertyId == "transform.y" })
        assertTrue(result.resultJson.contains("affectedClips"))
    }

    @Test
    fun sequentialWalkStartsFromAnimatedPositionAndKeepsEarlierKeyframes() {
        val fx = fixture()
        val first = fx.executor.execute(
            command(
                BridgeCapability.ANIMATE_CHARACTER,
                """{"action":"WALK","bodyClipId":"body","startOffsetUs":0,"durationUs":1000000,"direction":1,"distancePx":200}""",
                expectedRevision = 5L,
            ),
        )
        assertTrue(first.success)

        val second = fx.executor.execute(
            command(
                BridgeCapability.ANIMATE_CHARACTER,
                """{"action":"WALK","bodyClipId":"body","startOffsetUs":1500000,"durationUs":1000000,"direction":1,"distancePx":120}""",
                expectedRevision = 6L,
            ),
        )
        assertTrue(second.success)
        assertEquals(7L, second.projectRevision)

        val x = fx.session.state.project.findClip("body")!!.propertyAnimations.single { it.propertyId == "transform.x" }
        val byTime = x.keyframes.associate { it.offsetUs to (it.value as PropertyValue.Scalar).value }
        assertEquals(0.0, byTime.getValue(0L), 1e-9)
        assertEquals(200.0, byTime.getValue(1_000_000L), 1e-9)
        assertEquals(200.0, byTime.getValue(1_500_000L), 1e-9)
        assertEquals(320.0, byTime.getValue(2_500_000L), 1e-9)
    }

    @Test
    fun animateTalkAcceptsLipCuesAndMovesRiggedMouth() {
        val fx = fixture()
        val result = fx.executor.execute(
            command(
                BridgeCapability.ANIMATE_CHARACTER,
                """{"action":"TALK","bodyClipId":"body","mouthClipId":"mouth","eyesClipId":"eyes","startOffsetUs":0,"durationUs":1500000,"lipSyncCues":[{"offsetUs":100000,"durationUs":200000,"openness":0.4},{"offsetUs":600000,"durationUs":250000,"openness":1.0}]}""",
            ),
        )

        assertTrue(result.success)
        assertTrue(fx.session.state.project.findClip("mouth")!!.propertyAnimations.any { it.propertyId == "transform.scale" })
        assertTrue(fx.session.state.project.findClip("eyes")!!.propertyAnimations.any { it.propertyId == "transform.opacity" })
    }

    @Test
    fun animateSceneCrossfadeUpdatesBothSceneLayers() {
        val fx = fixture()
        val result = fx.executor.execute(
            command(
                BridgeCapability.ANIMATE_SCENE,
                """{"kind":"CROSSFADE","primaryClipId":"scene-a","incomingClipId":"scene-b","startOffsetUs":250000,"durationUs":1000000}""",
            ),
        )

        assertTrue(result.success)
        assertTrue(fx.session.state.project.findClip("scene-a")!!.propertyAnimations.any { it.propertyId == "transform.opacity" })
        assertTrue(fx.session.state.project.findClip("scene-b")!!.propertyAnimations.any { it.propertyId == "transform.opacity" })
    }

    @Test
    fun invalidAnimationPayloadFailsClosedWithoutRevisionChange() {
        val fx = fixture()
        val before = fx.session.state.project
        val result = fx.executor.execute(
            command(
                BridgeCapability.ANIMATE_CHARACTER,
                """{"action":"PICK_UP","bodyClipId":"body","heldObjectClipId":"missing","startOffsetUs":0,"durationUs":1000000}""",
            ),
        )

        assertFalse(result.success)
        assertEquals(5L, fx.session.state.revision)
        assertEquals(before, fx.session.state.project)
    }

    private data class Fixture(val session: ProjectSession, val executor: BridgeCommandExecutor)

    private fun fixture(): Fixture {
        val asset = Asset(
            id = "asset",
            type = AssetType.IMAGE,
            uri = "content://animation",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 1L,
            displayName = "frame.png",
            mimeType = "image/png",
            fingerprint = "fp",
        )
        fun clip(id: String) = Clip(id, asset.id, 0L, 3_000_000L, 0L, 3_000_000L)
        val project = Project.create("Animation Bridge", 1080, 1920, 30).copy(
            id = "project-1",
            assets = listOf(asset),
            tracks = listOf(
                Track("body-track", TrackType.VISUAL, 0, TrackCollisionPolicy.OVERLAY, clips = listOf(clip("body"))),
                Track("mouth-track", TrackType.VISUAL, 1, TrackCollisionPolicy.OVERLAY, clips = listOf(clip("mouth"))),
                Track("eyes-track", TrackType.VISUAL, 2, TrackCollisionPolicy.OVERLAY, clips = listOf(clip("eyes"))),
                Track("scene-a-track", TrackType.VISUAL, 3, TrackCollisionPolicy.OVERLAY, clips = listOf(clip("scene-a"))),
                Track("scene-b-track", TrackType.VISUAL, 4, TrackCollisionPolicy.OVERLAY, clips = listOf(clip("scene-b"))),
                Track("audio", TrackType.AUDIO, 5),
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
        expectedRevision: Long = 5L,
    ) = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = "msg-${capability.wireName}-$expectedRevision",
        kind = "command",
        deviceId = "device-1",
        projectId = "project-1",
        expectedRevision = expectedRevision,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 31_000L,
        nonce = "nonce-${capability.wireName}-$expectedRevision",
        capability = capability,
        payloadJson = payload,
    )
}
