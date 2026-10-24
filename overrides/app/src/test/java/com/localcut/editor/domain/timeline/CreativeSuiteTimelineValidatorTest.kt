package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeSuiteTimelineValidatorTest {
    @Test
    fun twoXClipUsesTimeMapDurationInsteadOfLegacyOneXRule() {
        val clip = clip(
            durationUs = 500_000L,
            timeMap = TimeMap(baseSpeed = 2.0),
        )

        val issues = TimelineValidator.validate(project(clip))

        assertFalse(issues.any { it.code == ProjectIssueCode.DURATION_SOURCE_MISMATCH })
    }

    @Test
    fun wrongPresentationDurationForTimeMapIsRejected() {
        val clip = clip(
            durationUs = 1_000_000L,
            timeMap = TimeMap(baseSpeed = 2.0),
        )

        assertTrue(
            TimelineValidator.validate(project(clip)).any { it.code == ProjectIssueCode.DURATION_SOURCE_MISMATCH },
        )
    }

    @Test
    fun registryParameterRangeViolationIsCreativeStateError() {
        val clip = clip(
            effects = listOf(
                EffectInstance(
                    id = "fx",
                    typeId = "localcut.color.brightness",
                    parameters = mapOf("amount" to EffectParameter.Number(2.0)),
                ),
            ),
        )

        assertTrue(
            TimelineValidator.validate(project(clip)).any { it.code == ProjectIssueCode.INVALID_CREATIVE_STATE },
        )
    }

    @Test
    fun freezeSourceOffsetCannotPointOutsideSourceRange() {
        val clip = clip(
            durationUs = 1_250_000L,
            timeMap = TimeMap(
                freezeSegments = listOf(
                    FreezeSegment(startOffsetUs = 500_000L, durationUs = 250_000L, sourceOffsetUs = 2_000_000L),
                ),
            ),
        )

        assertTrue(
            TimelineValidator.validate(project(clip)).any { it.code == ProjectIssueCode.INVALID_CREATIVE_STATE },
        )
    }

    private fun clip(
        durationUs: Long = 1_000_000L,
        timeMap: TimeMap = TimeMap(),
        effects: List<EffectInstance> = emptyList(),
    ): Clip = Clip(
        id = "clip-1",
        assetId = "asset-1",
        startUs = 0L,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = 1_000_000L,
        timeMap = timeMap,
        effects = effects,
    )

    private fun project(clip: Clip): Project {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 1_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp",
        )
        return Project.create("Creative", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
