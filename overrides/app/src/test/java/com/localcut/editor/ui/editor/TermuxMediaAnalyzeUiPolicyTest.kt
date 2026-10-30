package com.localcut.editor.ui.editor

import com.localcut.editor.termux.TermuxEngineJob
import com.localcut.editor.termux.TermuxEngineJobCapabilities
import com.localcut.editor.termux.TermuxEngineJobStatus
import com.localcut.editor.termux.TermuxEngineUiState
import com.localcut.editor.termux.TermuxMediaAnalysis
import com.localcut.editor.termux.TermuxMediaStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxMediaAnalyzeUiPolicyTest {
    @Test
    fun analysisRequiresConnectedJobCapableFfprobeEngineAndSelection() {
        val ready = TermuxEngineUiState.initial(true).connected(
            "0.4.0",
            TermuxEngineJobCapabilities.REQUIRED + setOf("PING", "GET_STATUS", "GET_CAPABILITIES", "FFPROBE"),
        )
        assertTrue(TermuxMediaAnalyzeUiPolicy.availability(ready, true).enabled)
        assertFalse(TermuxMediaAnalyzeUiPolicy.availability(ready, false).enabled)
        assertFalse(TermuxMediaAnalyzeUiPolicy.availability(ready.copy(capabilities = setOf("PING")), true).enabled)
        assertFalse(TermuxMediaAnalyzeUiPolicy.availability(ready.copy(capabilities = ready.capabilities - "FFPROBE"), true).enabled)
    }

    @Test
    fun successSummaryIncludesUsefulMediaDetails() {
        val message = TermuxMediaAnalyzeUiPolicy.resultMessage(
            TermuxEngineJob(
                "id",
                "ANALYZE_MEDIA",
                TermuxEngineJobStatus.SUCCEEDED,
                100,
                analysis = TermuxMediaAnalysis(
                    "mov,mp4",
                    2_000_000L,
                    5L,
                    64_000L,
                    listOf(TermuxMediaStream("video", "h264", 1920, 1080)),
                ),
            ),
        )
        assertTrue(message.contains("1920×1080"))
        assertTrue(message.contains("2.00s"))
    }
}
