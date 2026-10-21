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
        copy(selectedResolution = resolution)

    fun selectFps(fps: Int): ExportSettingsState = copy(selectedFps = fps)

    fun selectBitrate(bitrateBitsPerSecond: Int): ExportSettingsState =
        copy(selectedBitrateBitsPerSecond = bitrateBitsPerSecond)

    fun selectCodec(codec: ExportVideoCodec): ExportSettingsState = copy(selectedCodec = codec)

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
            val resolutions = ExportProfileOptions.resolutionsFor(project)
            val fps = ExportProfileOptions.fpsFor(project)
            val bitrates = ExportProfileOptions.bitrates()
            val codecs = ExportProfileOptions.codecsFor(capabilities)

            return ExportSettingsState(
                resolutionOptions = resolutions,
                fpsOptions = fps,
                bitrateOptions = bitrates,
                codecOptions = codecs,
                selectedResolution = ExportResolution(default.width, default.height),
                selectedFps = default.fps,
                selectedBitrateBitsPerSecond = default.videoBitrateBitsPerSecond,
                selectedCodec = default.codec,
            )
        }
    }
}
