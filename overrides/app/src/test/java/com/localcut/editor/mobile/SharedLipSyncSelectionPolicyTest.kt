package com.localcut.editor.mobile

import com.localcut.editor.domain.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLipSyncSelectionPolicyTest {
    private fun item(id: String, type: AssetType, startUs: Long = 0L) =
        SharedLipSyncSelectionPolicy.ImportedClip(id, type, startUs)

    @Test
    fun imageAndVoiceOnEmptyTimelineSelectImageRegardlessOfImportOrder() {
        val image = item("portrait", AssetType.IMAGE)
        val voice = item("voice", AssetType.AUDIO)
        assertEquals("portrait", SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(image, voice)))
        assertEquals("portrait", SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(voice, image)))
    }

    @Test
    fun doesNotStealSelectionForNonemptyOrAmbiguousProjects() {
        val image = item("portrait", AssetType.IMAGE)
        val voice = item("voice", AssetType.AUDIO)
        assertNull(SharedLipSyncSelectionPolicy.imageToSelect(false, listOf(image, voice)))
        assertNull(SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(image)))
        assertNull(SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(image, image)))
        assertNull(SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(image, item("other", AssetType.IMAGE))))
        assertNull(SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(item("video", AssetType.VIDEO), voice)))
        assertNull(SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(image, item("voice", AssetType.AUDIO, 4_000_000))))
        assertNull(SharedLipSyncSelectionPolicy.imageToSelect(true, listOf(item("", AssetType.IMAGE), voice)))
    }
}
