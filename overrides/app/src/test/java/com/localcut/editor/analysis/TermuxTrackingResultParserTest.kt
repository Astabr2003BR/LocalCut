package com.localcut.editor.analysis

import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxTrackingResultParserTest {
    @Test
    fun parsesSucceededTrackingJobAndBuildsStabilizationAnimations() {
        val json = """
            {
              "ok": true,
              "job": {
                "status": "SUCCEEDED",
                "output": {
                  "analysisMode": "opencv-global-motion-v1",
                  "analysis": {
                    "trackingMode": "opencv-global-motion-v1",
                    "framesDecoded": 7,
                    "frameRate": 30.0,
                    "samples": [
                      {"frameIndex":3,"timeUs":100000,"dx":2.0,"dy":1.0,"rotationDeg":1.0,"scale":1.01,"confidence":0.9},
                      {"frameIndex":6,"timeUs":200000,"dx":3.0,"dy":-1.0,"rotationDeg":2.0,"scale":1.0,"confidence":0.8}
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = TermuxTrackingResultParser.parse(json)

        assertEquals(7, parsed.framesDecoded)
        assertEquals(30.0, parsed.frameRate!!, 0.0)
        assertEquals(2, parsed.samples.size)
        assertEquals(100_000L, parsed.samples[0].offsetUs)
        assertEquals(2.0, parsed.samples[0].dx, 0.0)
        assertEquals(0.9, parsed.samples[0].confidence, 0.0)

        val animations = parsed.toStabilizationAnimations(strength = 1.0, minConfidence = 0.5)
        val x = animations.first { it.propertyId == "transform.x" }
        val y = animations.first { it.propertyId == "transform.y" }
        val rotation = animations.first { it.propertyId == "transform.rotationDeg" }

        assertEquals(3, x.keyframes.size)
        assertEquals(-5.0, (x.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)
        assertEquals(0.0, (y.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)
        assertEquals(-3.0, (rotation.keyframes.last().value as PropertyValue.Scalar).value, 1e-9)
    }

    @Test
    fun derivesTimestampFromFrameRateWhenWorkerOmitsTimeUs() {
        val json = """
            {
              "ok": true,
              "job": {
                "status": "SUCCEEDED",
                "output": {
                  "analysisMode": "opencv-global-motion-v1",
                  "analysis": {
                    "framesDecoded": 4,
                    "frameRate": 25.0,
                    "samples": [
                      {"frameIndex":2,"dx":1.0,"dy":0.0,"rotationDeg":0.0,"scale":1.0,"confidence":1.0}
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = TermuxTrackingResultParser.parse(json)

        assertEquals(80_000L, parsed.samples.single().offsetUs)
    }

    @Test
    fun rejectsFailedWrongModeMalformedOrUnorderedResults() {
        val failed = """{"ok":true,"job":{"status":"FAILED"}}"""
        val wrongMode = """
            {"ok":true,"job":{"status":"SUCCEEDED","output":{"analysisMode":"ffprobe-v1","analysis":{}}}}
        """.trimIndent()
        val invalidConfidence = """
            {"ok":true,"job":{"status":"SUCCEEDED","output":{"analysisMode":"opencv-global-motion-v1","analysis":{"framesDecoded":3,"frameRate":30.0,"samples":[{"frameIndex":1,"timeUs":1000,"dx":1.0,"dy":1.0,"rotationDeg":0.0,"scale":1.0,"confidence":2.0}]}}}}
        """.trimIndent()
        val unordered = """
            {"ok":true,"job":{"status":"SUCCEEDED","output":{"analysisMode":"opencv-global-motion-v1","analysis":{"framesDecoded":4,"frameRate":30.0,"samples":[{"frameIndex":2,"timeUs":2000,"dx":1.0,"dy":0.0,"rotationDeg":0.0,"scale":1.0,"confidence":1.0},{"frameIndex":3,"timeUs":1000,"dx":1.0,"dy":0.0,"rotationDeg":0.0,"scale":1.0,"confidence":1.0}]}}}}
        """.trimIndent()

        assertTrue(runCatching { TermuxTrackingResultParser.parse(failed) }.isFailure)
        assertTrue(runCatching { TermuxTrackingResultParser.parse(wrongMode) }.isFailure)
        assertTrue(runCatching { TermuxTrackingResultParser.parse(invalidConfidence) }.isFailure)
        assertTrue(runCatching { TermuxTrackingResultParser.parse(unordered) }.isFailure)
    }
}
