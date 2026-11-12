package com.localcut.editor.mobile

import com.localcut.editor.bridge.protocol.BridgeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileHandoffParserTest {
    @Test
    fun parsesBlinkHandoffWithPortraitDefaults() {
        val command = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=blink&returnTo=editor",
        ).getOrThrow()
        assertNull(command.capability)
        assertEquals(MobileHandoffReturnTarget.EDITOR, command.returnTarget)
        val region = requireNotNull(command.blinkRegion)
        assertEquals(0.37, region.leftX, 0.000001)
        assertEquals(0.63, region.rightX, 0.000001)
        assertEquals(0.36, region.y, 0.000001)
        assertTrue(command.description.contains("Desfazer") ||
            command.description.contains("desfeita"))
    }

    @Test
    fun blinkHandoffRejectsOverlappingOrUnsafeEyes() {
        val queries = listOf(
            "action=blink&leftX=NaN",
            "action=blink&rightX=1.0",
            "action=blink&leftX=0.51&rightX=0.53",
            "action=blink&radiusY=0.2",
            "action=blink&radiusX=0",
            "action=blink&y=0.001",
        )
        queries.forEach { query ->
            assertTrue(query, MobileHandoffParser.parse(
                "localcut://command?$query",
            ).isFailure)
        }
    }

    @Test
    fun portraitMotionHandoffAcceptsDataOnlyPresetsAndAdjustments() {
        val natural = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=blink&preset=natural" +
                "&intervalSeconds=2.8&breathStrength=0.60&swayStrength=0.30" +
                "&returnTo=editor",
        ).getOrThrow().blinkRegion!!.settings()
        assertEquals(2.8, natural.intervalSeconds, 0.000001)
        assertEquals(0.60, natural.breathStrength, 0.000001)
        assertEquals(0.30, natural.swayStrength, 0.000001)
        assertEquals(0.37, natural.leftX, 0.000001)
        assertEquals(0.63, natural.rightX, 0.000001)
    }

    @Test
    fun portraitMotionHandoffRejectsMalformedPresetsAndNumbers() {
        val rejects = listOf(
            "action=blink&preset=remoteScript",
            "action=blink&breathStrength=NaN",
            "action=blink&swayStrength=Infinity",
            "action=blink&intervalSeconds=0.1",
            "action=blink&blinkStrength=1.1",
            "action=blink&durationSeconds=0.5",
            "action=blink&leftX=0.49&rightX=0.50",
        )
        rejects.forEach {
            assertTrue(it, MobileHandoffParser.parse("localcut://command?$it").isFailure)
        }
    }

    @Test
    fun parsesConfirmedLocalLipSyncHandoff() {
        val result = MobileHandoffParser.parse(
            "https://localcut-bridge-v1.onrender.com/open?action=lip_sync&x=0.51&y=0.49&radiusX=0.07&radiusY=0.025&returnTo=chatgpt",
        ).getOrThrow()
        assertEquals(null, result.capability)
        assertEquals(0.51, result.lipSyncRegion!!.centerX, 0.00001)
        assertEquals(0.49, result.lipSyncRegion!!.centerY, 0.00001)
        assertEquals(MobileHandoffReturnTarget.CHATGPT, result.returnTarget)
        assertTrue(result.description.contains("Desfazer") || result.description.contains("desfeita"))
    }

    @Test
    fun rejectsUnsafeOrMissingLipSyncGeometry() {
        val malformed = listOf(
            "action=lip_sync",
            "action=lip_sync&x=NaN&y=0.49&radiusX=0.07&radiusY=0.025",
            "action=lip_sync&x=1.0&y=0.49&radiusX=0.07&radiusY=0.025",
            "action=lip_sync&x=0.01&y=0.49&radiusX=0.07&radiusY=0.025",
            "action=lip_sync&x=0.51&y=0.49&radiusX=0.0&radiusY=0.025",
            "action=lip_sync&x=0.51&y=0.001&radiusX=0.07&radiusY=0.025",
            "action=lip_sync&x=0.51&y=0.49&radiusX=0.07&radiusY=0.4",
        )
        malformed.forEach { query ->
            assertTrue("Invalid link accepted: $query",
                MobileHandoffParser.parse("https://localcut-bridge-v1.onrender.com/open?$query").isFailure)
        }
    }

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
