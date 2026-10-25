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

    @Test
    fun parsesAndResolvesSplitSelectedClipAt2300msWithoutClipId() {
        val command = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=split_selected_clip&positionUs=2300000&returnTo=chatgpt",
        ).getOrThrow()

        assertEquals(BridgeCapability.SPLIT_CLIP, command.capability)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, command.returnTarget)
        assertTrue(command.resolveSelectedClip)

        val payload = SelectedClipDeleteResolver.resolvePayload(
            command = command,
            selectedClipId = "clip-42",
        ).getOrThrow()

        assertEquals("{\"clipId\":\"clip-42\",\"positionUs\":2300000}", payload)
    }
}
