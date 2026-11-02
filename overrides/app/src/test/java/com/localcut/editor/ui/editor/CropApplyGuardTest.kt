package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropApplyGuardTest {
    private val captured = CompositingSettings(
        crop = CropSettings(left = 0.1, top = 0.1, right = 0.9, bottom = 0.9),
    )

    @Test
    fun appliesOnlyToTheSameSelectedClipAndUnchangedCompositingSnapshot() {
        assertTrue(
            CropApplyGuard.canApply(
                capturedClipId = "clip-a",
                capturedCompositing = captured,
                currentSelectedClipId = "clip-a",
                currentCompositing = captured,
            ),
        )
    }

    @Test
    fun rejectsResultAfterSelectionChanges() {
        assertFalse(
            CropApplyGuard.canApply(
                capturedClipId = "clip-a",
                capturedCompositing = captured,
                currentSelectedClipId = "clip-b",
                currentCompositing = captured,
            ),
        )
    }

    @Test
    fun rejectsResultAfterCompositingChanges() {
        assertFalse(
            CropApplyGuard.canApply(
                capturedClipId = "clip-a",
                capturedCompositing = captured,
                currentSelectedClipId = "clip-a",
                currentCompositing = captured.copy(crop = captured.crop.copy(left = 0.2)),
            ),
        )
    }

    @Test
    fun rejectsMissingClipSnapshot() {
        assertFalse(
            CropApplyGuard.canApply(
                capturedClipId = "clip-a",
                capturedCompositing = captured,
                currentSelectedClipId = "clip-a",
                currentCompositing = null,
            ),
        )
    }
}
