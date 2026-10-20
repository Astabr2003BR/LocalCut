package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSettingsStateTest {
    @Test
    fun projectDefaultsBecomeInitialSelection() {
        val project = Project.create("P", 1080, 1920, 30)

        val state = ExportSettingsState.from(
            project = project,
            capabilities = ExportCapabilities(hevcEncoderAvailable = true),
        )

        assertEquals(ExportResolution(1080, 1920), state.selectedResolution)
        assertEquals(30, state.selectedFps)
        assertEquals(8_000_000, state.selectedBitrateBitsPerSecond)
        assertEquals(ExportVideoCodec.H264, state.selectedCodec)
        assertTrue(state.codecOptions.contains(ExportVideoCodec.HEVC))
        assertEquals(ExportProfile.defaultFor(project), state.toProfile())
    }

    @Test
    fun selectionUpdatesProduceRequestedProfile() {
        val project = Project.create("P", 1080, 1920, 60)
        val initial = ExportSettingsState.from(
            project = project,
            capabilities = ExportCapabilities(hevcEncoderAvailable = true),
        )

        val selected = initial
            .selectResolution(ExportResolution(720, 1280))
            .selectFps(30)
            .selectBitrate(12_000_000)
            .selectCodec(ExportVideoCodec.HEVC)

        assertEquals(
            ExportProfile(
                width = 720,
                height = 1280,
                fps = 30,
                videoBitrateBitsPerSecond = 12_000_000,
                codec = ExportVideoCodec.HEVC,
            ),
            selected.toProfile(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unavailableCodecCannotBeSelected() {
        val state = ExportSettingsState.from(
            project = Project.create("P", 1080, 1920, 30),
            capabilities = ExportCapabilities(hevcEncoderAvailable = false),
        )

        state.selectCodec(ExportVideoCodec.HEVC)
    }
}
