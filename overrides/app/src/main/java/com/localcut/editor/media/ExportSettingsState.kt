package com.localcut.editor.media

import com.localcut.editor.domain.model.Project

data class ExportSettingsState(
    val resolutionOptions: List<ExportResolution>,
    val fpsOptions: List<Int>,
    val bitrateOptions: List<Int>,
    val codecOptions: List<ExportVideoCodec>,
    val selectedResolution: ExportResolution,
    val selectedFps: Int,
    val selectedBitrateBitsPerSecond: Int,
    val selectedCodec: ExportVideoCodec,
) {
    init {
        require(selectedResolution in resolutionOptions) { "selected resolution is not available" }
        require(selectedFps in fpsOptions) { "selected fps is not available" }
        require(selectedBitrateBitsPerSecond in bitrateOptions) { "selected bitrate is not available" }
        require(selectedCodec in codecOptions) { "selected codec is not available" }
    }

    fun selectResolution(resolution: ExportResolution): ExportSettingsState =
        copy(
            resolutionOptions = prioritize(resolutionOptions, resolution),
            selectedResolution = resolution,
        )

    fun selectFps(fps: Int): ExportSettingsState = copy(
        fpsOptions = prioritize(fpsOptions, fps),
        selectedFps = fps,
    )

    fun selectBitrate(bitrateBitsPerSecond: Int): ExportSettingsState = copy(
        bitrateOptions = prioritize(bitrateOptions, bitrateBitsPerSecond),
        selectedBitrateBitsPerSecond = bitrateBitsPerSecond,
    )

    fun selectCodec(codec: ExportVideoCodec): ExportSettingsState = copy(
        codecOptions = prioritize(codecOptions, codec),
        selectedCodec = codec,
    )

    fun toProfile(): ExportProfile = ExportProfile(
        width = selectedResolution.width,
        height = selectedResolution.height,
        fps = selectedFps,
        videoBitrateBitsPerSecond = selectedBitrateBitsPerSecond,
        codec = selectedCodec,
    )

    companion object {
        fun from(
            project: Project,
            capabilities: ExportCapabilities,
        ): ExportSettingsState {
            val default = ExportProfile.defaultFor(project)
            val selectedResolution = ExportResolution(default.width, default.height)
            val selectedFps = default.fps
            val selectedBitrate = default.videoBitrateBitsPerSecond
            val selectedCodec = default.codec

            return ExportSettingsState(
                resolutionOptions = prioritize(
                    ExportProfileOptions.resolutionsFor(project),
                    selectedResolution,
                ),
                fpsOptions = prioritize(
                    ExportProfileOptions.fpsFor(project),
                    selectedFps,
                ),
                bitrateOptions = prioritize(
                    ExportProfileOptions.bitrates(),
                    selectedBitrate,
                ),
                codecOptions = prioritize(
                    ExportProfileOptions.codecsFor(capabilities),
                    selectedCodec,
                ),
                selectedResolution = selectedResolution,
                selectedFps = selectedFps,
                selectedBitrateBitsPerSecond = selectedBitrate,
                selectedCodec = selectedCodec,
            )
        }

        private fun <T> prioritize(options: List<T>, selected: T): List<T> =
            if (selected !in options || options.firstOrNull() == selected) {
                options
            } else {
                listOf(selected) + options.filterNot { it == selected }
            }
    }
}
