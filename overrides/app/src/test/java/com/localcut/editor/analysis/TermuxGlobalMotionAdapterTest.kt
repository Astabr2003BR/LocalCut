package com.localcut.editor.analysis

import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.termux.TermuxGlobalMotionAnalysis
import com.localcut.editor.termux.TermuxGlobalMotionSample
import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxGlobalMotionAdapterTest {
    @Test
    fun typedWorkerResultUsesSameStabilizationMathAsParserPath() {
        val result = TermuxGlobalMotionAnalysis(
            framesDecoded = 7,
            frameRate = 30.0,
            samples = listOf(
                TermuxGlobalMotionSample(3, 100_000L, 2.0, 1.0, 1.0, 1.01, 0.9),
                TermuxGlobalMotionSample(6, 200_000L, 3.0, -1.0, 2.0, 1.0, 0.8),
            ),
        )

        val animations = TermuxGlobalMotionAdapter.toStabilizationAnimations(
            result = result,
            strength = 1.0,
            minConfidence = 0.5,
        )

        val x = animations.first { it.propertyId == "transform.x" }
        val y = animations.first { it.propertyId == "transform.y" }
        val rotation = animations.first { it.propertyId == "transform.rotationDeg" }
        assertEquals(-5.0, (x.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)
        assertEquals(0.0, (y.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)
        assertEquals(-3.0, (rotation.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)
    }

    @Test
    fun confidenceFilteringRemainsFailClosed() {
        val result = TermuxGlobalMotionAnalysis(
            framesDecoded = 4,
            frameRate = 30.0,
            samples = listOf(
                TermuxGlobalMotionSample(1, 33_333L, 10.0, 0.0, 0.0, 1.0, 0.1),
                TermuxGlobalMotionSample(2, 66_667L, 2.0, 0.0, 0.0, 1.0, 0.9),
            ),
        )

        val animations = TermuxGlobalMotionAdapter.toStabilizationAnimations(result, minConfidence = 0.5)
        val x = animations.first { it.propertyId == "transform.x" }
        assertEquals(-2.0, (x.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)
    }
}
