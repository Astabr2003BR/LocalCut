package com.localcut.editor.ui.editor

import com.localcut.editor.termux.TermuxEngineJob
import com.localcut.editor.termux.TermuxEngineJobCapabilities
import com.localcut.editor.termux.TermuxEngineJobStatus
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiState
import java.util.Locale

data class TermuxMediaAnalyzeAvailability(
    val enabled: Boolean,
    val message: String,
)

object TermuxMediaAnalyzeUiPolicy {
    fun availability(
        engineState: TermuxEngineUiState,
        hasSelectedAsset: Boolean,
    ): TermuxMediaAnalyzeAvailability = when {
        engineState.phase != TermuxEngineUiPhase.CONNECTED ->
            TermuxMediaAnalyzeAvailability(false, "Conecte o Termux Agent Engine para analisar mídia.")
        !hasSelectedAsset ->
            TermuxMediaAnalyzeAvailability(false, "Selecione um clipe de mídia na timeline.")
        !engineState.capabilities.containsAll(TermuxEngineJobCapabilities.REQUIRED) ->
            TermuxMediaAnalyzeAvailability(false, "Atualize o Termux Agent Engine para habilitar jobs de mídia.")
        "FFPROBE" !in engineState.capabilities ->
            TermuxMediaAnalyzeAvailability(false, "FFprobe indisponível. No Termux, instale: pkg install ffmpeg")
        else -> TermuxMediaAnalyzeAvailability(true, "Pronto para analisar a mídia selecionada.")
    }

    fun resultMessage(job: TermuxEngineJob): String = when (job.status) {
        TermuxEngineJobStatus.SUCCEEDED -> {
            val analysis = job.analysis
            if (analysis == null) {
                "Análise concluída."
            } else {
                val video = analysis.streams.firstOrNull { it.codecType == "video" }
                val details = buildList {
                    if (video?.width != null && video.height != null) add("${video.width}×${video.height}")
                    analysis.durationUs?.let { add(String.format(Locale.US, "%.2fs", it / 1_000_000.0)) }
                    analysis.formatName?.takeIf { it.isNotBlank() }?.let(::add)
                }
                if (details.isEmpty()) "Análise concluída." else "Análise concluída • ${details.joinToString(" • ")}"
            }
        }
        TermuxEngineJobStatus.FAILED -> "Falha na análise • ${job.errorCode ?: "ANALYZE_MEDIA_FAILED"}"
        TermuxEngineJobStatus.CANCELLED -> "Análise cancelada."
        TermuxEngineJobStatus.WAITING_APPROVAL -> "Análise aguardando aprovação."
        TermuxEngineJobStatus.RUNNING -> "Analisando mídia… ${job.progress}%"
        TermuxEngineJobStatus.QUEUED -> "Análise na fila… ${job.progress}%"
    }
}
