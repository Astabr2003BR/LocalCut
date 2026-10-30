package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.ObjectTrackingPathPlanner
import com.localcut.editor.domain.animation.ObjectTrackingPositionSample
import com.localcut.editor.domain.history.HistoryManager
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.termux.TermuxObjectTrackingAnalysis
import com.localcut.editor.termux.TermuxObjectTrackingSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingApplyCommandFactoryTest {
    @Test
    fun trackingXYAppliesAsOneUndoableCommandAndPreservesOtherAnimation() {
        val scale = PropertyAnimation(
            propertyId = "transform.scale",
            keyframes = listOf(
                PropertyKeyframe("scale-0", 0L, PropertyValue.Scalar(1.0)),
                PropertyKeyframe("scale-1", 500_000L, PropertyValue.Scalar(1.2)),
            ),
        )
        val initial = projectWithClip(propertyAnimations = listOf(scale))
        val clip = initial.findClip("clip-1")!!
        val captured = ObjectTrackingApplyGuard.capture(clip)
        val tracking = ObjectTrackingPathPlanner.plan(
            samples = listOf(ObjectTrackingPositionSample(100_000L, 0.1, 0.2, 0.9)),
            renderWidthPx = 1000.0,
            renderHeightPx = 500.0,
            baseX = 0.0,
            baseY = 0.0,
        )
        val command = ObjectTrackingApplyCommandFactory.create(captured, clip, tracking)
        val history = HistoryManager(initial)

        history.execute(command)

        val applied = history.project.findClip("clip-1")!!.propertyAnimations
        assertEquals(setOf("transform.scale", "transform.x", "transform.y"), applied.map { it.propertyId }.toSet())
        assertTrue(ObjectTrackingUiPolicy.hasGeneratedObjectTracking(applied))
        assertTrue(history.undo())
        assertEquals(listOf(scale), history.project.findClip("clip-1")!!.propertyAnimations)
    }

    @Test
    fun analysisIsMappedThroughClipSpeedAndSourceFitBeforeAtomicCommand() {
        val clip = projectWithClip()
            .findClip("clip-1")!!
            .copy(
                durationUs = 500_000L,
                timeMap = TimeMap(baseSpeed = 2.0),
                transform = TransformValue(x = 10.0, y = 5.0),
            )
        val captured = ObjectTrackingApplyGuard.capture(clip)
        val analysis = TermuxObjectTrackingAnalysis(
            framesDecoded = 8,
            frameWidth = 1200,
            frameHeight = 900,
            frameRate = 30.0,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            samples = listOf(
                TermuxObjectTrackingSample(
                    frameIndex = 6,
                    timeUs = 200_000L,
                    dxNorm = 0.10,
                    dyNorm = 0.10,
                    centerXNorm = 0.60,
                    centerYNorm = 0.60,
                    confidence = 0.95,
                ),
            ),
        )

        val command = ObjectTrackingApplyCommandFactory.createFromAnalysis(
            captured = captured,
            currentClip = clip,
            analysis = analysis,
            projectWidth = 1600,
            projectHeight = 900,
        )

        val x = command.propertyAnimations.single { it.propertyId == "transform.x" }.keyframes
        val y = command.propertyAnimations.single { it.propertyId == "transform.y" }.keyframes
        assertEquals(listOf(0L, 100_000L), x.map { it.offsetUs })
        assertEquals(10.0, (x.first().value as PropertyValue.Scalar).value, 0.0)
        assertEquals(130.0, (x.last().value as PropertyValue.Scalar).value, 0.0)
        assertEquals(5.0, (y.first().value as PropertyValue.Scalar).value, 0.0)
        assertEquals(95.0, (y.last().value as PropertyValue.Scalar).value, 0.0)
    }

    @Test
    fun analysisRangeMustStillMatchCapturedClipAtApplyBoundary() {
        val clip = projectWithClip().findClip("clip-1")!!
        val captured = ObjectTrackingApplyGuard.capture(clip)
        val analysis = TermuxObjectTrackingAnalysis(
            framesDecoded = 4,
            frameWidth = 1000,
            frameHeight = 500,
            frameRate = 30.0,
            sourceInUs = 100_000L,
            sourceOutUs = 1_000_000L,
            samples = listOf(
                TermuxObjectTrackingSample(1, 33_333L, 0.01, 0.0, 0.5, 0.5, 1.0),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingApplyCommandFactory.createFromAnalysis(
                captured = captured,
                currentClip = clip,
                analysis = analysis,
                projectWidth = 1000,
                projectHeight = 500,
            )
        }

        assertEquals("OBJECT_TRACKING_SOURCE_RANGE_MISMATCH", error.message)
    }

    @Test
    fun staleTrimCannotProduceApplyCommand() {
        val initial = projectWithClip()
        val clip = initial.findClip("clip-1")!!
        val captured = ObjectTrackingApplyGuard.capture(clip)
        val changed = clip.copy(sourceInUs = 100_000L, sourceOutUs = 1_100_000L)
        val tracking = ObjectTrackingPathPlanner.plan(
            samples = listOf(ObjectTrackingPositionSample(100_000L, 0.1, 0.0, 0.9)),
            renderWidthPx = 1000.0,
            renderHeightPx = 500.0,
            baseX = 0.0,
            baseY = 0.0,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ObjectTrackingApplyCommandFactory.create(captured, changed, tracking)
        }
        assertEquals("OBJECT_TRACKING_RESULT_STALE", error.message)
    }

    private fun projectWithClip(propertyAnimations: List<PropertyAnimation> = emptyList()): Project {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 1L,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp-1",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            propertyAnimations = propertyAnimations,
        )
        return Project.create("Tracking", 1000, 500, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
