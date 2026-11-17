package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.BridgeJobSnapshot
import com.localcut.editor.bridge.agent.BridgeJobState
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asBoolean
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required
import com.localcut.editor.data.project.jsonString

/** Identity of the exact rendered MP4; not a claim that external visual review occurred. */
internal data class EditorAiReviewSource(val projectId: String, val revision: Long, val jobId: String, val sha256: String)

/** Only metadata, never image bytes, paths or raw remote error messages. */
internal data class EditorAiExportReview(
    val title: String,
    val detail: String,
    val request: String? = null,
    val source: EditorAiReviewSource? = null,
) {
    companion object {
        fun from(job: BridgeJobSnapshot?, projectId: String, revision: Long): EditorAiExportReview {
            if (job == null || job.sourceProjectId != projectId || job.sourceRevision == null)
                return EditorAiExportReview("Sem exportação pelo Bridge", "Exporte o projeto pelo GPT conectado para obter a inspeção do MP4. Exportações manuais não entram neste painel.")
            val source = job.sourceRevision
            val version = "Revisão exportada: $source." + if (source != revision) " O projeto atual está na revisão $revision; a análise não representa a versão atual." else ""
            when (job.state) {
                BridgeJobState.QUEUED -> return EditorAiExportReview("Exportação aguardando", version)
                BridgeJobState.RUNNING -> return EditorAiExportReview("Exportando: ${job.progressPercent ?: 0}%", version)
                BridgeJobState.FAILED -> return EditorAiExportReview("Exportação falhou", "$version Consulte os erros do Bridge.")
                BridgeJobState.CANCELLED -> return EditorAiExportReview("Exportação cancelada", version)
                BridgeJobState.SUCCEEDED -> Unit
            }
            val unavailable = EditorAiExportReview("MP4 exportado • inspeção indisponível", "$version Não há evidência técnica válida para esta exportação.")
            return runCatching {
                val result = MiniJson.parse(requireNotNull(job.resultJson)).asObject()
                require(result.required("sourceProjectId").asString() == projectId && result.required("sourceRevision").asLong() == source)
                val report = result.required("technicalReview").asObject()
                val status = report.required("status").asString()
                if (status == "unavailable") return unavailable
                require(status == "technical_checks_passed" || status == "needs_review")
                val width = report.required("width").asLong()
                val height = report.required("height").asLong()
                val duration = report.required("durationUs").asLong()
                val audio = report.required("audioTracks").asLong()
                val decoded = report.required("decodedFrames").asLong()
                val sampled = report.required("sampledFrames").asLong()
                val encoded = report.required("encodedSampleFrames").asLong()
                val dark = report.required("nearBlackSamplePositionsUs").asArray().values.size
                val durationMatches = report.required("durationMatches").asBoolean()
                val resolutionMatches = report.required("resolutionMatches").asBoolean()
                val hash = report.required("sha256").asString()
                require(width > 0 && height > 0 && duration > 0 && audio >= 0 && sampled in 1L..3L &&
                    decoded in 0L..sampled && encoded in 0L..decoded && dark <= decoded && hash.matches(Regex("[a-f0-9]{64}")))
                val warnings = buildList {
                    if (!durationMatches) add("Duração diferente da esperada.")
                    if (!resolutionMatches) add("Resolução diferente da esperada.")
                    if (decoded != sampled) add("Decodificação parcial ou indisponível.")
                    if (dark > 0) add("$dark amostra(s) quase preta(s); pode ser intencional.")
                }
                val detail = buildString {
                    append(version); append("\n${width}×${height} • ${duration / 1_000L} ms • $audio faixa(s) de áudio")
                    append("\nQuadros decodificados: $decoded/$sampled.")
                    warnings.forEach { append("\n"); append(it) }
                    append("\nAmostragem técnica, não aprovação visual ou sincronização completa.")
                }
                val identity = if (encoded == sampled && encoded > 0 && source == revision)
                    EditorAiReviewSource(projectId, source, job.jobId, hash) else null
                val request = if (identity != null) buildString {
                    append("LocalCut: consulte get_export_review_frames no dispositivo pareado, projeto ")
                    append(jsonString(projectId)); append(" com payload ")
                    append("{\"jobId\":"); append(jsonString(job.jobId)); append(",\"sourceRevision\":"); append(source); append("}. ")
                    append("Confira se SHA-256 do MP4 é $hash. Analise os quadros retornados e descreva os problemas visuais. ")
                    append("A evidência é temporária (até 10 minutos, substituída pela próxima exportação). Se indisponível, informe; não invente imagens ou aprovação. Não altere o projeto sem minha autorização. ")
                    append("Se houver correção visual necessária e você tiver inspecionado os quadros, proponha um JSON local com projectId, expectedRevision, operations e review. ")
                    append("O objeto review deve conter exatamente jobId, sha256 e findings (texto de 1 a 1000 caracteres, descreva o que foi observado e as limitações). ")
                    append("Use jobId "); append(jsonString(job.jobId)); append(" e sha256 "); append(jsonString(hash))
                    append(". Não use operações destrutivas. Se houver correção, use submit_export_review_proposal no dispositivo autorizado, com expectedRevision atual e payload {planJson: JSON do plano como string}; aguarde aprovação local, não execute project_edit. Se a ferramenta estiver indisponível, retorne o JSON para colar. Se não houver correção, responda somente com a análise; não invente alterações. A amostragem não confirma qualidade de todo o vídeo ou sincronização de áudio.")
                } else null
                EditorAiExportReview(
                    if (status == "technical_checks_passed" && warnings.isEmpty()) "MP4 exportado • verificações técnicas concluídas" else "MP4 exportado • requer revisão",
                    detail, request, identity)
            }.getOrElse { unavailable }
        }
    }
}
