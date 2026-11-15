package com.localcut.editor.mobile

/** Read-only summary of the exact parsed plan presented before export consent. */
internal object AutomaticVideoApproval {
    fun describe(plan: AutomaticVideoPlan, hasAdditionalAudio: Boolean): String =
        buildString {
            append("Novo MP4: ")
            append(plan.width).append(" × ").append(plan.height).append(" • 30 fps\n")
            append(plan.scenes.size).append(" cenas • ").append(plan.durationUs / 1000).append(" ms\n")
            append(plan.scenes.map { it.imageIndex }.distinct().size).append(" mídias visuais utilizadas\n")
            append(if (hasAdditionalAudio) "Com áudio adicional." else "Sem áudio adicional.")
            append("\nO projeto aberto e os arquivos originais serão preservados. A renderização cria um novo arquivo.")
        }
}
