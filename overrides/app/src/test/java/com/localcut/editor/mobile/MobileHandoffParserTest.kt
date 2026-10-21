package com.localcut.editor.mobile

import com.localcut.editor.bridge.protocol.BridgeCapability
import org.junit.Assert.assertEquals
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
    fun rejectsUnsupportedAction() {
        val result = MobileHandoffParser.parse(
            "localcut://command?action=wipe_project",
        )

        assertTrue(result.isFailure)
    }
}
