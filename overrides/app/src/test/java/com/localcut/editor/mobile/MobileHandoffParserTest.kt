package com.localcut.editor.mobile

import com.localcut.editor.bridge.protocol.BridgeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileHandoffParserTest {
    @Test
    fun parsesHttpsAddTextHandoffAndReturnsToChatGptByDefault() {
        val result = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=add_text&id=t1&text=Oi%20mundo&startUs=5000000&durationUs=3000000",
        ).getOrThrow()

        assertEquals(BridgeCapability.ADD_TEXT, result.capability)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
        assertTrue(result.payloadJson.contains("\"text\":\"Oi mundo\""))
        assertTrue(result.payloadJson.contains("\"startUs\":5000000"))
    }

    @Test
    fun parsesCustomSchemeSeekHandoffAndKeepsEditorByDefault() {
        val result = MobileHandoffParser.parse(
            "localcut://command?action=seek&positionUs=2500000",
        ).getOrThrow()

        assertEquals(BridgeCapability.SEEK, result.capability)
        assertEquals(MobileHandoffReturnTarget.EDITOR, result.returnTarget)
        assertEquals("{\"positionUs\":2500000}", result.payloadJson)
    }

    @Test
    fun customSchemeCanExplicitlyReturnToChatGpt() {
        val result = MobileHandoffParser.parse(
            "localcut://command?action=undo&returnTo=chatgpt",
        ).getOrThrow()

        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
    }

    @Test
    fun parsesMobilePairingHandoffWithoutTurningItIntoAnEditorCommand() {
        val result = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=pair_mobile&code=105223&returnTo=chatgpt",
        ).getOrThrow()

        assertEquals("105223", result.mobilePairingCode)
        assertEquals(null, result.capability)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
        assertTrue(result.description.contains("105223"))
    }

    @Test
    fun parsesHttpsImportUrlWithoutTurningItIntoAnEditorCommand() {
        val mediaUrl = "https://cdn.example/media/video.mp4"
        val result = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=import_url&url=https%3A%2F%2Fcdn.example%2Fmedia%2Fvideo.mp4&returnTo=chatgpt",
        ).getOrThrow()

        assertEquals(mediaUrl, result.remoteMediaUrl)
        assertNull(result.capability)
        assertNull(result.mobilePairingCode)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
        assertEquals("Baixar e importar mídia remota no LocalCut", result.description)
    }

    @Test
    fun rejectsHttpImportUrl() {
        val result = MobileHandoffParser.parse(
            "localcut://command?action=import_url&url=http%3A%2F%2Fexample.com%2Fvideo.mp4&returnTo=chatgpt",
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsMobilePairingCodeThatIsNotExactlySixDigits() {
        val result = MobileHandoffParser.parse(
            "localcut://command?action=pair_mobile&code=12345&returnTo=chatgpt",
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsUntrustedHttpsHost() {
        val result = MobileHandoffParser.parse(
            "https://evil.example/open?action=undo",
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsTrimWithoutExactlyOneEdge() {
        val result = MobileHandoffParser.parse(
            "localcut://command?action=trim_clip&clipId=c1&newStartUs=1&newEndUs=2",
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun parsesDeleteAllVisualClipsAsLocalSemanticAction() {
        val result = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=delete_all_visual_clips&returnTo=chatgpt",
        ).getOrThrow()

        assertNull(result.capability)
        assertTrue(result.deleteAllVisualClips)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
        assertTrue(result.description.contains("vídeos e imagens"))
    }

    @Test
    fun parsesGenericProjectEditPayload() {
        val result = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=project_edit&payload=%7B%22operations%22%3A%5B%7B%22op%22%3A%22deleteByKind%22%2C%22kinds%22%3A%5B%22AUDIO%22%5D%7D%5D%7D&returnTo=chatgpt",
        ).getOrThrow()

        assertEquals(BridgeCapability.PROJECT_EDIT, result.capability)
        assertEquals("{\"operations\":[{\"op\":\"deleteByKind\",\"kinds\":[\"AUDIO\"]}]}", result.payloadJson)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
    }

    @Test
    fun rejectsProjectEditWithoutPayloadOrWithOversizePayload() {
        assertTrue(
            MobileHandoffParser.parse(
                "https://localcut-bridge-v1.onrender.com/open?action=project_edit&returnTo=chatgpt",
            ).isFailure,
        )
        val payload = "a".repeat(7_001)
        val encoded = java.net.URLEncoder.encode(payload, java.nio.charset.StandardCharsets.UTF_8.name())
        assertTrue(
            MobileHandoffParser.parse(
                "https://localcut-bridge-v1.onrender.com/open?action=project_edit&payload=$encoded&returnTo=chatgpt",
            ).isFailure,
        )
    }

    @Test
    fun rejectsUnsupportedAction() {
        val result = MobileHandoffParser.parse(
            "localcut://command?action=wipe_project",
        )

        assertTrue(result.isFailure)
    }
}
