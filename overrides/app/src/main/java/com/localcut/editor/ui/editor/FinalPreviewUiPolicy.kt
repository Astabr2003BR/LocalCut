package com.localcut.editor.ui.editor

object FinalPreviewUiPolicy {
    fun failureMessage(error: Throwable): String {
        val messages = generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .toList()
        return if (messages.any { it.contains("Video frame processing", ignoreCase = true) }) {
            "A prévia final falhou ao processar um quadro. O projeto continua editável."
        } else {
            "Não foi possível renderizar a prévia final. O projeto continua editável."
        }
    }
}
