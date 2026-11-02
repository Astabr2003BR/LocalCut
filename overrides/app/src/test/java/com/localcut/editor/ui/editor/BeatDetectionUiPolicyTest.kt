package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatDetectionUiPolicyTest {
    @Test
    fun sensitivityPresetsAreValidAndOrderedFromMoreToLessSensitive() {
        val high = BeatDetectionUiPolicy.settings(BeatDetectionSensitivity.HIGH)
        val standard = BeatDetectionUiPolicy.settings(BeatDetectionSensitivity.STANDARD)
        val low = BeatDetectionUiPolicy.settings(BeatDetectionSensitivity.LOW)

        assertTrue(high.validationIssues().isEmpty())
        assertTrue(standard.validationIssues().isEmpty())
        assertTrue(low.validationIssues().isEmpty())
        assertTrue(high.thresholdMultiplier < standard.thresholdMultiplier)
        assertTrue(standard.thresholdMultiplier < low.thresholdMultiplier)
        assertTrue(high.minimumIntervalMs < standard.minimumIntervalMs)
        assertTrue(standard.minimumIntervalMs < low.minimumIntervalMs)
    }

    @Test
    fun labelsStayShortForMobileTimelineRail() {
        BeatDetectionSensitivity.entries.forEach { sensitivity ->
            val label = BeatDetectionUiPolicy.label(sensitivity)
            assertTrue(label.isNotBlank())
            assertTrue(label.length <= 8)
        }
        assertEquals("Padrão", BeatDetectionUiPolicy.label(BeatDetectionSensitivity.STANDARD))
    }
}
