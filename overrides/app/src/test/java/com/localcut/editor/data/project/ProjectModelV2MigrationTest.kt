package com.localcut.editor.data.project

import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.timeline.ProjectIssueCode
import com.localcut.editor.domain.timeline.TimelineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectModelV2MigrationTest {
    @Test
    fun v1FixtureMigratesToCurrentSchemaWithoutChangingAssetsOrTimelineData() {
        val project = ProjectJson.decode(v1Fixture())

        assertEquals(4, project.schemaVersion)
        assertEquals("legacy-project", project.id)
        assertEquals("Projeto legado", project.title)
        assertEquals(1080, project.width)
        assertEquals(1920, project.height)
        assertEquals(30, project.fps)
        assertEquals(listOf("asset-video"), project.assets.map { it.id })
        assertEquals(listOf("visual-1", "audio-1"), project.tracks.map { it.id })
        assertEquals(listOf("clip-1"), project.tracks.first().clips.map { it.id })
        assertEquals(1_500_000L, project.tracks.first().clips.single().startUs)
        assertEquals(2_000_000L, project.tracks.first().clips.single().durationUs)

        val encoded = ProjectJson.encode(project)
        assertTrue(encoded.contains("\"schemaVersion\":4"))
        assertTrue(encoded.contains("\"collisionPolicy\":\"EXCLUSIVE\""))
    }

    @Test
    fun v2ProjectWithMultipleTracksMigratesAndRoundTripsExactly() {
        val decoded = ProjectJson.decode(v2EmptyMultitrackFixture())

        assertEquals(4, decoded.schemaVersion)
        assertEquals(3, decoded.tracks.size)
        assertEquals(decoded, ProjectJson.decode(ProjectJson.encode(decoded)))
    }

    @Test
    fun exclusiveTrackRejectsOverlapWhileOverlayTrackAllowsIt() {
        val overlay = ProjectJson.decode(v2OverlapFixture("OVERLAY"))
        val exclusive = overlay.copy(
            tracks = overlay.tracks.map { track ->
                if (track.id == "visual-1") {
                    track.copy(collisionPolicy = TrackCollisionPolicy.EXCLUSIVE)
                } else {
                    track
                }
            },
        )

        assertTrue(
            TimelineValidator.validate(exclusive).any { it.code == ProjectIssueCode.OVERLAPPING_CLIPS },
        )
        assertFalse(
            TimelineValidator.validate(overlay).any { it.code == ProjectIssueCode.OVERLAPPING_CLIPS },
        )
    }

    private fun v1Fixture(): String = """
        {
          "id":"legacy-project",
          "schemaVersion":1,
          "title":"Projeto legado",
          "width":1080,
          "height":1920,
          "fps":30,
          "assets":[
            {
              "id":"asset-video",
              "type":"VIDEO",
              "uri":"content://video/legacy",
              "sourceDurationUs":10000000,
              "sizeBytes":1234,
              "displayName":"legacy.mp4",
              "mimeType":"video/mp4",
              "fingerprint":"legacy-fingerprint"
            }
          ],
          "tracks":[
            {
              "id":"visual-1",
              "type":"VISUAL",
              "order":0,
              "muted":false,
              "hidden":false,
              "locked":false,
              "clips":[
                {
                  "id":"clip-1",
                  "assetId":"asset-video",
                  "startUs":1500000,
                  "durationUs":2000000,
                  "sourceInUs":1000000,
                  "sourceOutUs":3000000,
                  "transform":{"x":0.0,"y":0.0,"scale":1.0,"rotationDeg":0.0,"opacity":1.0},
                  "opacity":1.0,
                  "volume":1.0,
                  "keyframes":[]
                }
              ]
            },
            {
              "id":"audio-1",
              "type":"AUDIO",
              "order":1,
              "muted":false,
              "hidden":false,
              "locked":false,
              "clips":[]
            }
          ]
        }
    """.trimIndent()

    private fun v2EmptyMultitrackFixture(): String = """
        {
          "id":"v2-project",
          "schemaVersion":2,
          "title":"V2",
          "width":1080,
          "height":1920,
          "fps":30,
          "assets":[],
          "tracks":[
            {"id":"visual-1","type":"VISUAL","order":0,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"clips":[]},
            {"id":"visual-overlay-1","type":"VISUAL","order":1,"collisionPolicy":"OVERLAY","muted":false,"hidden":false,"locked":false,"clips":[]},
            {"id":"audio-1","type":"AUDIO","order":2,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"clips":[]}
          ]
        }
    """.trimIndent()

    private fun v2OverlapFixture(policy: String): String = """
        {
          "id":"overlap-$policy",
          "schemaVersion":2,
          "title":"Overlap",
          "width":1080,
          "height":1920,
          "fps":30,
          "assets":[
            {"id":"v","type":"VIDEO","uri":"content://v","sourceDurationUs":10000000,"sizeBytes":1,"displayName":"v.mp4","mimeType":"video/mp4","fingerprint":"v"}
          ],
          "tracks":[
            {
              "id":"visual-1",
              "type":"VISUAL",
              "order":0,
              "collisionPolicy":"$policy",
              "muted":false,
              "hidden":false,
              "locked":false,
              "clips":[
                {"id":"a","assetId":"v","startUs":0,"durationUs":2000000,"sourceInUs":0,"sourceOutUs":2000000,"transform":{"x":0.0,"y":0.0,"scale":1.0,"rotationDeg":0.0,"opacity":1.0},"opacity":1.0,"volume":1.0,"keyframes":[]},
                {"id":"b","assetId":"v","startUs":1000000,"durationUs":2000000,"sourceInUs":2000000,"sourceOutUs":4000000,"transform":{"x":0.0,"y":0.0,"scale":1.0,"rotationDeg":0.0,"opacity":1.0},"opacity":1.0,"volume":1.0,"keyframes":[]}
              ]
            },
            {"id":"audio-1","type":"AUDIO","order":1,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"clips":[]}
          ]
        }
    """.trimIndent()
}
