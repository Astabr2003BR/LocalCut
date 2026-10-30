package com.localcut.editor.mobile

import com.localcut.editor.bridge.protocol.BridgeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSelectedClipCommandTest {
    @Test
    fun selectedClipResolverBuildsStandardDeletePayload() {
        val command = MobileHandoffCommand(
            capability = BridgeCapability.DELETE_CLIP,
            payloadJson = "{\"selectedClip\":true}",
            description = "Excluir o clipe selecionado",
            resolveSelectedClip = true,
        )

        val payload = SelectedClipDeleteResolver.resolvePayload(command, "clip-1").getOrThrow()

        assertEquals("{\"clipId\":\"clip-1\"}", payload)
    }

    @Test
    fun selectedClipResolverRejectsMissingSelection() {
        val command = MobileHandoffCommand(
            capability = BridgeCapability.DELETE_CLIP,
            payloadJson = "{\"selectedClip\":true}",
            description = "Excluir o clipe selecionado",
            resolveSelectedClip = true,
        )

        val result = SelectedClipDeleteResolver.resolvePayload(command, null)

        assertTrue(result.isFailure)
        assertEquals("CLIP_NOT_FOUND", result.exceptionOrNull()?.message)
    }
}
