package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.Project
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

class BridgeFlatMouthAnimationTest {
    @Test
    fun flatImageTalkAcceptsMouthRegionAndAppliesOwnedEffect() {
        val fx = fixture()
        val result = fx.executor.execute(
            command(
                payload = """{"action":"TALK","bodyClipId":"body","startOffsetUs":100000,"durationUs":1200000,"intensity":0.8,"mouthRegion":{"centerX":0.50,"centerY":0.66,"radiusX":0.10,"radiusY":0.045}}""",
            ),
        )

        assertTrue(result.success)
        assertEquals(6L, result.projectRevision)
        val body = fx.session.state.project.findClip("body")!!
        val effect = body.effects.single { it.typeId == "localcut.character.mouth_warp" }
        assertEquals(EffectParameter.Point(0.50, 0.66), effect.parameters["center"])
        assertEquals(EffectParameter.Number(0.10), effect.parameters["radiusX"])
        assertEquals(EffectParameter.Number(0.045), effect.parameters["radiusY"])
        assertTrue(effect.animations.single { it.propertyId == "openness" }.keyframes.size >= 5)
        assertTrue(result.resultJson.contains("\"flatMouthEffectApplied\":true"))
    }

    @Test
    fun invalidMouthRegionFailsClosedWithoutRevisionChange() {
        val fx = fixture()
        val before = fx.session.state.project
        val result = fx.executor.execute(
            command(
                payload = """{"action":"TALK","bodyClipId":"body","startOffsetUs":0,"durationUs":1000000,"mouthRegion":{"centerX":1.2,"centerY":0.66,"radiusX":0.10,"radiusY":0.05}}""",
            ),
        )

        assertFalse(result.success)
        assertEquals(5L, fx.session.state.revision)
        assertEquals(before, fx.session.state.project)
    }

    @Test
    fun malformedMouthRegionFailsClosedInsteadOfBeingIgnored() {
        val fx = fixture()
        val result = fx.executor.execute(
            command(
                payload = """{"action":"TALK","bodyClipId":"body","startOffsetUs":0,"durationUs":1000000,"mouthRegion":{"centerX":"half","centerY":0.66,"radiusX":0.10,"radiusY":0.05}}""",
            ),
        )

        assertFalse(result.success)
        assertEquals(5L, fx.session.state.revision)
    }

    @Test
    fun explicitMouthClipKeepsRiggedPathWhenMouthRegionIsAlsoPresent() {
        val fx = fixture()
        val result = fx.executor.execute(
            command(
                payload = """{"action":"TALK","bodyClipId":"body","mouthClipId":"mouth","startOffsetUs":0,"durationUs":1000000,"mouthRegion":{"centerX":0.50,"centerY":0.66,"radiusX":0.10,"radiusY":0.05}}""",
            ),
        )

        assertTrue(result.success)
        assertTrue(fx.session.state.project.findClip("mouth")!!.propertyAnimations.any { it.propertyId == "transform.scale" })
        assertTrue(fx.session.state.project.findClip("body")!!.effects.none { it.typeId == "localcut.character.mouth_warp" })
        assertTrue(result.resultJson.contains("\"flatMouthEffectApplied\":false"))
    }

    private data class Fixture(val session: ProjectSession, val executor: BridgeCommandExecutor)

    private fun fixture(): Fixture {
        val asset = Asset(
            id = "asset",
            type = AssetType.IMAGE,
            uri = "content://flat-character",
            sourceDurationUs = 3_000_000L,
            sizeBytes = 1L,
            displayName = "character.png",
            mimeType = "image/png",
            fingerprint = "flat-character",
        )
        fun clip(id: String) = Clip(id, asset.id, 0L, 2_000_000L, 0L, 2_000_000L)
        val project = Project.create("Bridge flat mouth", 1080, 1920, 30).copy(
            id = "project-flat-mouth",
            assets = listOf(asset),
            tracks = listOf(
                Track("body-track", TrackType.VISUAL, 0, TrackCollisionPolicy.OVERLAY, clips = listOf(clip("body"))),
                Track("mouth-track", TrackType.VISUAL, 1, TrackCollisionPolicy.OVERLAY, clips = listOf(clip("mouth"))),
                Track("audio", TrackType.AUDIO, 2),
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

    private fun command(payload: String) = BridgeCommandEnvelope(
        protocolVersion = 1,
        messageId = "msg-flat-mouth-${payload.hashCode()}",
        kind = "command",
        deviceId = "device-1",
        projectId = "project-flat-mouth",
        expectedRevision = 5L,
        issuedAtEpochMs = 1_000L,
        expiresAtEpochMs = 31_000L,
        nonce = "nonce-flat-mouth-${payload.hashCode()}",
        capability = BridgeCapability.ANIMATE_CHARACTER,
        payloadJson = payload,
    )
}
