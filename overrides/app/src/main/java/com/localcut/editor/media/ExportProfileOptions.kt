package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import kotlin.math.roundToInt

data class ExportResolution(
    val width: Int,
    val height: Int,
)

object ExportProfileOptions {
    fun resolutionsFor(project: Project): List<ExportResolution> {
        require(project.width > 0 && project.height > 0) { "project dimensions must be > 0" }
        val source = ExportResolution(project.width, project.height)
        val shortEdge = minOf(project.width, project.height)
        val portrait = project.height >= project.width
        val ratio = project.width.toDouble() / project.height.toDouble()

        val scaled = STANDARD_SHORT_EDGES
            .asSequence()
            .filter { it <= shortEdge }
            .map { targetShort ->
                if (portrait) {
                    val width = evenAtMost(targetShort, project.width)
                    val height = evenAtMost((width / ratio).roundToInt(), project.height)
                    ExportResolution(width, height)
                } else {
                    val height = evenAtMost(targetShort, project.height)
                    val width = evenAtMost((height * ratio).roundToInt(), project.width)
                    ExportResolution(width, height)
                }
            }
            .filter { it.width > 0 && it.height > 0 }
            .toMutableList()

        scaled += source
        return scaled.distinct().sortedBy { it.width.toLong() * it.height.toLong() }
    }

    fun fpsFor(project: Project): List<Int> =
        (STANDARD_FPS + project.fps)
            .filter { it > 0 && it <= project.fps }
            .distinct()
            .sorted()

    fun bitrates(): List<Int> = STANDARD_VIDEO_BITRATES

    fun codecsFor(capabilities: ExportCapabilities): List<ExportVideoCodec> = buildList {
        add(ExportVideoCodec.H264)
        if (capabilities.hevcEncoderAvailable) add(ExportVideoCodec.HEVC)
    }

    private fun evenAtMost(requested: Int, maximum: Int): Int {
        val bounded = requested.coerceAtMost(maximum)
        return if (bounded % 2 == 0) bounded else bounded - 1
    }

    private val STANDARD_SHORT_EDGES = listOf(480, 720, 1080, 1440, 2160)
    private val STANDARD_FPS = listOf(24, 25, 30, 50, 60)
    private val STANDARD_VIDEO_BITRATES = listOf(
        2_000_000,
        4_000_000,
        8_000_000,
        12_000_000,
        16_000_000,
        24_000_000,
        32_000_000,
        50_000_000,
    )
}
