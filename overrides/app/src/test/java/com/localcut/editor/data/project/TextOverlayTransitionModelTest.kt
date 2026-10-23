package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayTransitionModelTest {
    @Test
    fun v2ProjectMigratesToCurrentSchemaWithEmptyCreativeLayers() {
        val decoded = ProjectJson.decode(v2Fixture())

        assertEquals(BuildContract.PROJECT_SCHEMA_VERSION, decoded.schemaVersion)
        assertTrue(decoded.textOverlays.isEmpty())
        assertTrue(decoded.transitions.isEmpty())
        assertTrue(ProjectJson.encode(decoded).contains("\"schemaVersion\":${BuildContract.PROJECT_SCHEMA_VERSION}"))
    }

    @Test
    fun currentRoundTripPreservesTextOverlayStyleAndTransition() {
        val video = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 6_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video-fingerprint",
        )
        val project = Project.create("Creative", 1080, 1920, 30).copy(
            assets = listOf(video),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip("clip-a", "video", 0L, 3_000_000L, 0L, 3_000_000L),
                        Clip("clip-b", "video", 3_000_000L, 3_000_000L, 3_000_000L, 6_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            textOverlays = listOf(
                TextOverlay(
                    id = "title-1",
                    startUs = 500_000L,
                    durationUs = 2_000_000L,
                    text = "LocalCut",
                    style = TextStyle(
                        fontSizePx = 72.0,
                        alignment = TextHorizontalAlignment.CENTER,
                        fillArgb = 0xFFFFFFFF.toInt(),
                        strokeArgb = 0xFF000000.toInt(),
                        strokeWidthPx = 3.0,
                        shadowArgb = 0x99000000.toInt(),
                        shadowRadiusPx = 6.0,
                        shadowOffsetX = 2.0,
                        shadowOffsetY = 4.0,
                    ),
                    transform = TransformValue(x = 120.0, y = -180.0, scale = 0.9, rotationDeg = 4.0),
                    opacity = 0.85,
                    zIndex = 10,
                ),
            ),
            transitions = listOf(
                ClipTransition(
                    id = "transition-1",
                    fromClipId = "clip-a",
                    toClipId = "clip-b",
                    durationUs = 500_000L,
                    kind = TransitionKind.DISSOLVE,
                ),
            ),
        )

        val encoded = ProjectJson.encode(project)
        val decoded = ProjectJson.decode(encoded)

        assertTrue(encoded.contains("\"schemaVersion\":${BuildContract.PROJECT_SCHEMA_VERSION}"))
        assertEquals(project, decoded)
        assertEquals("LocalCut", decoded.textOverlays.single().text)
        assertEquals(TransitionKind.DISSOLVE, decoded.transitions.single().kind)
    }

    private fun v2Fixture(): String = """
        {
          "id":"legacy-v2",
          "schemaVersion":2,
          "title":"Legacy V2",
          "width":1080,
          "height":1920,
          "fps":30,
          "assets":[],
          "tracks":[
            {"id":"visual-1","type":"VISUAL","order":0,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"clips":[]},
            {"id":"audio-1","type":"AUDIO","order":1,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"clips":[]}
          ]
        }
    """.trimIndent()
}
