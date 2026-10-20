package com.localcut.editor.audio

import com.localcut.editor.data.project.ProjectJson
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineOps
import com.localcut.editor.media.ProjectRenderPlanCompiler
import com.localcut.editor.media.RenderAssetResolver
import com.localcut.editor.media.RenderMode
import com.localcut.editor.media.RenderSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioControlsModelTest {
    private val resolver = RenderAssetResolver { asset -> Result.success("resolved://${asset.id}") }

    @Test
    fun legacyProjectDefaultsFadeAndSoloWhileRoundTripPreservesNewControls() {
        val legacy = ProjectJson.decode(legacyFixture())
        val legacyClip = legacy.findClip("audio-clip")!!
        val legacyTrack = legacy.tracks.first { it.id == "audio-1" }

        assertEquals(0L, legacyClip.fadeInUs)
        assertEquals(0L, legacyClip.fadeOutUs)
        assertFalse(legacyTrack.solo)

        val edited = legacy.copy(
            tracks = legacy.tracks.map { track ->
                if (track.id == "audio-1") {
                    track.copy(
                        solo = true,
                        clips = track.clips.map { it.copy(fadeInUs = 500_000L, fadeOutUs = 750_000L) },
                    )
                } else track
            },
        )
        val roundTrip = ProjectJson.decode(ProjectJson.encode(edited))
        val clip = roundTrip.findClip("audio-clip")!!
        val track = roundTrip.tracks.first { it.id == "audio-1" }

        assertEquals(500_000L, clip.fadeInUs)
        assertEquals(750_000L, clip.fadeOutUs)
        assertTrue(track.solo)
    }

    @Test
    fun splitKeepsOnlyTheOriginalOuterFades() {
        val project = projectWithAudioTracks().copy(
            tracks = projectWithAudioTracks().tracks.map { track ->
                if (track.id == "audio-1") {
                    track.copy(clips = listOf(track.clips.single().copy(fadeInUs = 1_000_000L, fadeOutUs = 2_000_000L)))
                } else track
            },
        )

        val split = TimelineOps.splitClip(project, "audio-a", 3_000_000L)
        val clips = split.tracks.first { it.id == "audio-1" }.clips

        assertEquals(2, clips.size)
        assertEquals(1_000_000L, clips[0].fadeInUs)
        assertEquals(0L, clips[0].fadeOutUs)
        assertEquals(0L, clips[1].fadeInUs)
        assertEquals(2_000_000L, clips[1].fadeOutUs)
    }

    @Test
    fun renderPlanHonorsMuteSoloAndCarriesFadeEnvelopeIntoAudibleAudioLayer() {
        val base = projectWithAudioTracks()
        val soloProject = base.copy(
            tracks = base.tracks.map { track ->
                when (track.id) {
                    "audio-1" -> track.copy(muted = false, solo = false)
                    "audio-2" -> track.copy(muted = false, solo = true)
                    else -> track
                }
            },
        )

        val soloPlan = ProjectRenderPlanCompiler.compile(soloProject, RenderMode.EXPORT, resolver).getOrThrow()

        assertEquals(listOf("audio-2"), soloPlan.audioLayers.map { it.sourceTrackId })
        val soloMedia = soloPlan.audioLayers.single().segments.filterIsInstance<RenderSegment.Media>().single()
        assertEquals("audio-b", soloMedia.clipId)
        assertEquals(250_000L, soloMedia.fadeInUs)
        assertEquals(500_000L, soloMedia.fadeOutUs)

        val mutedProject = base.copy(
            tracks = base.tracks.map { track ->
                when (track.id) {
                    "audio-1" -> track.copy(muted = true, solo = false)
                    "audio-2" -> track.copy(muted = false, solo = false)
                    else -> track
                }
            },
        )
        val mutedPlan = ProjectRenderPlanCompiler.compile(mutedProject, RenderMode.EXPORT, resolver).getOrThrow()
        assertEquals(listOf("audio-2"), mutedPlan.audioLayers.map { it.sourceTrackId })
    }

    private fun projectWithAudioTracks(): Project {
        val visualAsset = Asset("visual", AssetType.VIDEO, "content://visual", 6_000_000L, 1L, "v.mp4", "video/mp4", "vf")
        val audioA = Asset("audio-a-asset", AssetType.AUDIO, "content://audio-a", 6_000_000L, 1L, "a.mp3", "audio/mpeg", "af-a")
        val audioB = Asset("audio-b-asset", AssetType.AUDIO, "content://audio-b", 6_000_000L, 1L, "b.mp3", "audio/mpeg", "af-b")
        return Project.create("Audio", 1080, 1920, 30).copy(
            assets = listOf(visualAsset, audioA, audioB),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(Clip("visual-clip", "visual", 0L, 6_000_000L, 0L, 6_000_000L)),
                ),
                Track(
                    id = "audio-1",
                    type = TrackType.AUDIO,
                    order = 1,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(Clip("audio-a", "audio-a-asset", 0L, 6_000_000L, 0L, 6_000_000L)),
                ),
                Track(
                    id = "audio-2",
                    type = TrackType.AUDIO,
                    order = 2,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip(
                            "audio-b",
                            "audio-b-asset",
                            0L,
                            6_000_000L,
                            0L,
                            6_000_000L,
                            fadeInUs = 250_000L,
                            fadeOutUs = 500_000L,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun legacyFixture(): String = """
        {
          "id":"legacy-audio",
          "schemaVersion":2,
          "title":"Legacy audio",
          "width":1080,
          "height":1920,
          "fps":30,
          "assets":[
            {"id":"v","type":"VIDEO","uri":"content://v","sourceDurationUs":6000000,"sizeBytes":1,"displayName":"v.mp4","mimeType":"video/mp4","fingerprint":"v"},
            {"id":"a","type":"AUDIO","uri":"content://a","sourceDurationUs":6000000,"sizeBytes":1,"displayName":"a.mp3","mimeType":"audio/mpeg","fingerprint":"a"}
          ],
          "tracks":[
            {"id":"visual-1","type":"VISUAL","order":0,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"clips":[{"id":"visual-clip","assetId":"v","startUs":0,"durationUs":6000000,"sourceInUs":0,"sourceOutUs":6000000,"transform":{"x":0.0,"y":0.0,"scale":1.0,"rotationDeg":0.0,"opacity":1.0},"opacity":1.0,"volume":1.0,"keyframes":[]}]},
            {"id":"audio-1","type":"AUDIO","order":1,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"clips":[{"id":"audio-clip","assetId":"a","startUs":0,"durationUs":6000000,"sourceInUs":0,"sourceOutUs":6000000,"transform":{"x":0.0,"y":0.0,"scale":1.0,"rotationDeg":0.0,"opacity":1.0},"opacity":1.0,"volume":1.0,"keyframes":[]}]}
          ]
        }
    """.trimIndent()
}
