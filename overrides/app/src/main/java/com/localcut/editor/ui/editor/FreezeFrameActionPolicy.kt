package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.TimeMap

internal object FreezeFrameActionPolicy {
    fun canCreate(
        timeMap: TimeMap,
        fadeInUs: Long,
        fadeOutUs: Long,
        presentationOffsetUs: Long,
        clipDurationUs: Long,
    ): Boolean {
        if (clipDurationUs <= 0L) return false
        if (timeMap.validationIssues().isNotEmpty()) return false
        if (timeMap.freezeSegments.isNotEmpty()) return false
        if (timeMap.reverse || timeMap.speedPoints.isNotEmpty()) return false
        if (fadeInUs != 0L || fadeOutUs != 0L) return false
        return presentationOffsetUs >= 0L && presentationOffsetUs < clipDurationUs
    }

    fun unavailableReason(
        timeMap: TimeMap,
        fadeInUs: Long,
        fadeOutUs: Long,
        presentationOffsetUs: Long,
        clipDurationUs: Long,
    ): String = when {
        timeMap.validationIssues().isNotEmpty() ->
            "Freeze indisponível: mapa de tempo inválido."
        timeMap.freezeSegments.isNotEmpty() ->
            "Remova o Freeze atual antes de criar outro."
        timeMap.reverse ->
            "Freeze não é combinado com Reverse lógico nesta versão."
        timeMap.speedPoints.isNotEmpty() ->
            "Freeze com curva de velocidade ainda é bloqueado nesta versão."
        fadeInUs != 0L || fadeOutUs != 0L ->
            "Freeze com fade de áudio ainda é bloqueado para preservar a exportação. Remova o fade antes de congelar o frame."
        presentationOffsetUs < 0L || presentationOffsetUs >= clipDurationUs ->
            "Posicione o playhead dentro do clipe para congelar o frame atual."
        else ->
            "Freeze indisponível para este clipe."
    }
}
