package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.*
import com.localcut.editor.domain.timeline.ProjectIssueCode
import com.localcut.editor.domain.timeline.TimelineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeSuiteProjectJsonTest {
    @Test
    fun latestSchemaRoundTripsCreativeClipStateExactly() {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://localcut/video",
            sourceDurationUs = 5_000_000L,
            sizeBytes = 100L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
            propertyAnimations = listOf(
                PropertyAnimation(
                    propertyId = "transform.x",
                    keyframes = listOf(
                        PropertyKeyframe("x0", 0L, PropertyValue.Scalar(0.0)),
                        PropertyKeyframe(
                            "x1",
                            4_000_000L,
                            PropertyValue.Scalar(100.0),
                            Easing(EasingKind.CUBIC_BEZIER, CubicBezierCurve(0.2, 0.0, 0.8, 1.0)),
                        ),
                    ),
                ),
            ),
            compositing = CompositingSettings(
                blendMode = BlendMode.SCREEN,
                masks = listOf(
                    MaskSpec(
                        id = "mask-1",
                        kind = MaskKind.FREEFORM,
                        points = listOf(MaskPoint(0.1, 0.2), MaskPoint(0.9, 0.8)),
                        invert = true,
                        featherPx = 8.0,
                        expansionPx = 2.0,
                        opacity = 0.75,
                    ),
                ),
                chromaKey = ChromaKeySettings(
                    enabled = true,
                    keyArgb = 0xFF00FF00.toInt(),
                    threshold = 0.3,
                    softness = 0.2,
                    spillSuppression = 0.4,
                    edgeFeatherPx = 3.0,
                ),
                crop = CropSettings(CropMode.FILL, 0.1, 0.1, 0.9, 0.9),
            ),
            effects = listOf(
                EffectInstance(
                    id = "fx-1",
                    typeId = "localcut.color.basic",
                    parameters = mapOf(
                        "amount" to EffectParameter.Number(0.5),
                        "enabled" to EffectParameter.Bool(true),
                        "color" to EffectParameter.Color(0xFF123456.toInt()),
                        "label" to EffectParameter.Text("cinematic"),
                        "center" to EffectParameter.Point(0.5, 0.5),
                    ),
                ),
            ),
            timeMap = TimeMap(
                baseSpeed = 1.25,
                reverse = true,
                speedPoints = listOf(SpeedPoint(0L, 1.0), SpeedPoint(2_000_000L, 2.0)),
                freezeSegments = listOf(FreezeSegment(1_000_000L, 250_000L, 750_000L)),
            ),
        )
        val project = Project.create("Creative", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )

        val encoded = ProjectJson.encode(project)
        val decoded = ProjectJson.decode(encoded)

        assertEquals(5, BuildContract.PROJECT_SCHEMA_VERSION)
        assertTrue(encoded.contains("\"schemaVersion\":5"))
        assertEquals(clip.propertyAnimations, decoded.findClip("clip-1")!!.propertyAnimations)
        assertEquals(clip.compositing, decoded.findClip("clip-1")!!.compositing)
        assertEquals(clip.effects, decoded.findClip("clip-1")!!.effects)
        assertEquals(clip.timeMap, decoded.findClip("clip-1")!!.timeMap)
    }

    @Test
    fun schemaV3MigratesCreativeFieldsToNeutralDefaults() {
        val decoded = ProjectJson.decode(v3Fixture())
        val clip = decoded.findClip("clip-1")!!

        assertEquals(5, decoded.schemaVersion)
        assertTrue(clip.propertyAnimations.isEmpty())
        assertEquals(CompositingSettings(), clip.compositing)
        assertTrue(clip.effects.isEmpty())
        assertEquals(TimeMap(), clip.timeMap)
    }

    @Test
    fun validatorRejectsInvalidCreativeStateBeforePersistence() {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://v",
            sourceDurationUs = 1_000_000L,
            sizeBytes = 1L,
            displayName = "v.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp",
        )
        val bad = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            effects = listOf(
                EffectInstance("fx", "localcut.bad", parameters = mapOf("x" to EffectParameter.Number(Double.NaN))),
            ),
        )
        val project = Project.create("Bad", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(bad)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )

        assertTrue(TimelineValidator.validate(project).any { it.code == ProjectIssueCode.INVALID_CREATIVE_STATE })
    }

    private fun v3Fixture(): String = """
        {
          "id":"legacy-v3",
          "schemaVersion":3,
          "title":"Legacy V3",
          "width":1080,
          "height":1920,
          "fps":30,
          "assets":[{"id":"asset-1","type":"VIDEO","uri":"content://v","sourceDurationUs":1000000,"sizeBytes":1,"displayName":"v.mp4","mimeType":"video/mp4","fingerprint":"fp"}],
          "tracks":[
            {"id":"visual-1","type":"VISUAL","order":0,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"solo":false,"clips":[
              {"id":"clip-1","assetId":"asset-1","startUs":0,"durationUs":1000000,"sourceInUs":0,"sourceOutUs":1000000,"transform":{"x":0.0,"y":0.0,"scale":1.0,"rotationDeg":0.0,"opacity":1.0},"opacity":1.0,"volume":1.0,"fadeInUs":0,"fadeOutUs":0,"keyframes":[]}
            ]},
            {"id":"audio-1","type":"AUDIO","order":1,"collisionPolicy":"EXCLUSIVE","muted":false,"hidden":false,"locked":false,"solo":false,"clips":[]}
          ],
          "textOverlays":[],
          "transitions":[]
        }
    """.trimIndent()
}
