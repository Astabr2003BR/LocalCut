package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportProfileOptionsTest {
    @Test
    fun portraitResolutionOptionsPreserveAspectAndNeverUpscale() {
        val project = Project.create("Portrait", 1080, 1920, 30)

        val options = ExportProfileOptions.resolutionsFor(project)

        assertTrue(options.contains(ExportResolution(720, 1280)))
        assertTrue(options.contains(ExportResolution(1080, 1920)))
        assertFalse(options.any { it.width > project.width || it.height > project.height })
        options.forEach { option ->
            val sourceRatio = project.width.toDouble() / project.height.toDouble()
            val optionRatio = option.width.toDouble() / option.height.toDouble()
            assertTrue(kotlin.math.abs(sourceRatio - optionRatio) < 0.002)
            assertEquals(0, option.width % 2)
            assertEquals(0, option.height % 2)
        }
    }

    @Test
    fun fpsOptionsNeverExceedProjectTimelineFps() {
        val project = Project.create("Fps", 1920, 1080, 30)

        val options = ExportProfileOptions.fpsFor(project)

        assertTrue(options.contains(24))
        assertTrue(options.contains(30))
        assertFalse(options.any { it > project.fps })
    }

    @Test
    fun hevcOptionAppearsOnlyWhenEncoderCapabilityIsVerified() {
        assertEquals(
            listOf(ExportVideoCodec.H264),
            ExportProfileOptions.codecsFor(ExportCapabilities(hevcEncoderAvailable = false)),
        )
        assertEquals(
            listOf(ExportVideoCodec.H264, ExportVideoCodec.HEVC),
            ExportProfileOptions.codecsFor(ExportCapabilities(hevcEncoderAvailable = true)),
        )
    }
}
