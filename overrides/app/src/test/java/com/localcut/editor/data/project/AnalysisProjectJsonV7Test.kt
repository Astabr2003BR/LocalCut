package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.analysis.*
import com.localcut.editor.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisProjectJsonV7Test {
    @Test
    fun schemaV7RoundTripsTrackingStabilizationAndReframe() {
        val asset = Asset("v", AssetType.VIDEO, "content://v", 2_000_000L, 1L, "v.mp4", "video/mp4", "fp")
        val tracking = TrackingData(
            target = TrackingTarget(TrackingTargetKind.OBJECT, 0.5, 0.5, "subject"),
            samples = listOf(
                TrackingSample(0L, 0.4, 0.5, 1.0, 0.0, 0.9),
                TrackingSample(1_000_000L, 0.6, 0.55, 1.1, 5.0, 0.8),
            ),
            sourceAnalyzerId = "local.tracker",
        )
        val stabilization = StabilizationData(
            samples = listOf(
                StabilizationSample(0L, 2.0, -1.0, 0.5, 1.02),
                StabilizationSample(1_000_000L, 1.0, 0.0, 0.2, 1.01),
            ),
            cropScale = 1.05,
            sourceAnalyzerId = "local.stabilizer",
        )
        val clip = Clip(
            id = "c",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
            analysis = ClipAnalysisState(tracking, stabilization),
        )
        val reframe = AutoReframeData(
            aspectWidth = 9,
            aspectHeight = 16,
            samples = listOf(
                ReframeSample(0L, 0.5, 0.5, 1.0),
                ReframeSample(1_000_000L, 0.55, 0.45, 1.1),
            ),
            sourceAnalyzerId = "local.reframe",
        )
        val project = Project.create("Analysis", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            analysis = ProjectAnalysisState(autoReframe = reframe),
        )

        val encoded = ProjectJson.encode(project)
        val decoded = ProjectJson.decode(encoded)

        assertEquals(7, BuildContract.PROJECT_SCHEMA_VERSION)
        assertTrue(encoded.contains("\"schemaVersion\":7"))
        assertEquals(clip.analysis, decoded.findClip("c")!!.analysis)
        assertEquals(project.analysis, decoded.analysis)
    }

    @Test
    fun schemaV6MigratesAnalysisToNeutralDefaults() {
        val v6 = ProjectJson.encode(Project.create("Legacy", 1080, 1920, 30))
            .replace("\"schemaVersion\":7", "\"schemaVersion\":6")
            .replace(Regex(",\"analysis\":\\{.*$"), "}")

        val decoded = ProjectJson.decode(v6)

        assertEquals(7, decoded.schemaVersion)
        assertEquals(ProjectAnalysisState(), decoded.analysis)
        assertTrue(decoded.tracks.flatMap { it.clips }.all { it.analysis == ClipAnalysisState() })
    }
}
