package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class FinalPreviewUiPolicyTest {
    @Test
    fun videoFrameProcessingFailureIsExplainedWithoutMakingTheProjectLookBroken() {
        val error = IllegalStateException("Video frame processing error")

        assertEquals(
            "A prévia final falhou ao processar um quadro. O projeto continua editável.",
            FinalPreviewUiPolicy.failureMessage(error),
        )
    }

    @Test
    fun genericFailureStillKeepsScopeLimitedToFinalPreview() {
        assertEquals(
            "Não foi possível renderizar a prévia final. O projeto continua editável.",
            FinalPreviewUiPolicy.failureMessage(IllegalStateException("codec failed")),
        )
    }
}
