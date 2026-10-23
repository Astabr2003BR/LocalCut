package com.localcut.editor.mobile

import com.localcut.editor.bridge.protocol.BridgeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSelectedClipHandoffTest {
    @Test
    fun parsesDeleteSelectedClipHandoffWithoutRequiringClipId() {
        val result = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=delete_selected_clip&returnTo=chatgpt",
        ).getOrThrow()

        assertEquals(BridgeCapability.DELETE_CLIP, result.capability)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
        assertTrue(result.payloadJson.contains("\"selectedClip\":true"))
    }
}
