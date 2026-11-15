package com.localcut.editor.ui.editor

import com.localcut.editor.bridge.agent.ProjectEditPayloadParser
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.required
import com.localcut.editor.domain.history.ProjectEditBatchCommand
import com.localcut.editor.domain.history.ProjectEditOperation
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession
import com.localcut.editor.session.ProjectSessionState

/**
 * Local paste/approve flow. No remote credentials, no implicit authorization.
 * All methods are called on the editor main thread. No approval is persisted.
 */
internal class EditorAiApprovalGate {
    internal data class Approval internal constructor(
        val projectId: String,
        val revision: Long,
        val draft: String,
        val operationCount: Int,
        val summary: String,
    )
    private data class Pending(val approval: Approval, val project: Project, val command: ProjectEditBatchCommand)
    private var pending: Pending? = null

    fun cancel() { pending = null }

    fun prepare(draft: String, state: ProjectSessionState): Result<Approval> = runCatching {
        pending = null
        require(draft.isNotBlank() && draft.length <= MAX_BYTES && draft.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Plano vazio ou acima de 8 KiB."
        }
        val root = MiniJson.parse(draft).asObject()
        require(root.fields.keys == setOf("projectId", "expectedRevision", "operations")) { "Use projectId, expectedRevision e operations." }
        val id = root.required("projectId").asString()
        val revision = root.required("expectedRevision").asLong()
        require(id == state.project.id && revision == state.revision) { "Projeto ou revisão desatualizados. Copie o contexto novamente." }
        val operations = ProjectEditPayloadParser.parseOperations(root)
        require(operations.size in 1..8) { "Use de 1 a 8 operações." }
        val labels = operations.map { operation ->
            when (operation) {
                is ProjectEditOperation.PatchClip -> {
                    require(operation.startUs == null && operation.volume == null && operation.transform == null) { "Correção visual não altera tempo ou áudio." }
                    val clip = requireNotNull(state.project.findClip(operation.clipId)) { "Clipe não encontrado." }
                    require(state.project.assets.any { it.id == clip.assetId && it.type != AssetType.AUDIO }) { "Selecione uma mídia visual." }
                    require(listOf(operation.x, operation.y, operation.scale, operation.rotationDeg, operation.opacity).any { it != null }) { "Alteração vazia." }
                    "Transformação/opacidade: ${operation.clipId}"
                }
                is ProjectEditOperation.PatchText -> {
                    require(operation.startUs == null && operation.durationUs == null) { "Correção visual não altera duração do texto." }
                    require(operation.text == null || operation.text.length in 1..500) { "Texto inválido." }
                    "Texto/estilo: ${operation.overlayId}"
                }
                is ProjectEditOperation.AddEffect -> "Adicionar efeito: ${operation.clipId} / ${operation.effectId}"
                is ProjectEditOperation.PatchEffect -> "Ajustar efeito: ${operation.clipId} / ${operation.effectId}"
                is ProjectEditOperation.SetEffectKeyframe -> "Keyframe: ${operation.clipId} / ${operation.effectId}"
                else -> error("Operação não permitida nesta correção visual. Use o editor tradicional para cortes, áudio ou exclusões.")
            }
        }
        val command = ProjectEditBatchCommand(operations)
        // Dry-run on immutable model; no session dispatch, history, preview or persistence.
        val candidate = command.apply(state.project).project
        require(candidate.assets == state.project.assets && TimelineMath.durationUs(candidate) == TimelineMath.durationUs(state.project)) {
            "A correção modificaria mídias ou duração."
        }
        val approval = Approval(id, revision, draft, operations.size, labels.joinToString("\n"))
        pending = Pending(approval, state.project, command)
        approval
    }

    fun apply(approval: Approval, currentDraft: String, session: ProjectSession): Result<Long> = runCatching {
        val accepted = pending
        pending = null // one attempt only, including conflicts or failures
        require(accepted != null && accepted.approval === approval && currentDraft == approval.draft) { "Aprovação expirada. Valide novamente." }
        val before = session.state
        require(before.project.id == approval.projectId && before.revision == approval.revision && before.project == accepted.project) {
            "Projeto mudou após a validação. Copie o contexto e valide novamente."
        }
        session.dispatch(EditorAction.Execute(accepted.command))
        val after = session.state
        check(after.revision > before.revision && after.project != before.project) { "Alteração não aplicada. Confira as camadas e bloqueios." }
        after.revision
    }

    companion object {
        const val MAX_BYTES = 8192

        /** Share only bounded editable metadata; never source URIs, paths, tokens or credentials. */
        fun context(state: ProjectSessionState): String = buildString {
            val p = state.project
            append("LocalCut Editor IA — proponha uma correção visual somente para este projeto.\n")
            append("Responda apenas JSON com projectId, expectedRevision e operations (1–8). ")
            append("Operações permitidas: patchClip (clipId, x, y, scale, rotationDeg, opacity), patchText (overlayId, text, x, y, scale, rotationDeg, opacity, fontSizePx, fillArgb, zIndex), addEffect, patchEffect, setEffectKeyframe. Preserve áudio, duração, mídias, textos e camadas não solicitados. Não invente IDs.\n")
            append("Contexto: {\"projectId\":"); append(jsonString(p.id))
            append(",\"expectedRevision\":"); append(state.revision)
            append(",\"width\":"); append(p.width); append(",\"height\":"); append(p.height)
            append(",\"durationUs\":"); append(TimelineMath.durationUs(p))
            val visual = p.tracks.flatMap { track -> track.clips.map { track to it } }
                .filter { (_, clip) -> p.assets.any { it.id == clip.assetId && it.type != AssetType.AUDIO } }
            append(",\"clips\":[")
            visual.take(32).forEachIndexed { i, (track, clip) ->
                if (i > 0) append(',')
                append("{\"id\":"); append(jsonString(clip.id)); append(",\"trackLocked\":"); append(track.locked)
                append(",\"startUs\":"); append(clip.startUs); append(",\"durationUs\":"); append(clip.durationUs)
                append(",\"x\":"); append(clip.transform.x); append(",\"y\":"); append(clip.transform.y)
                append(",\"scale\":"); append(clip.transform.scale); append(",\"rotationDeg\":"); append(clip.transform.rotationDeg)
                append(",\"opacity\":"); append(clip.opacity); append('}')
            }
            append("],\"textOverlays\":[")
            p.textOverlays.take(32).forEachIndexed { i, text ->
                if (i > 0) append(',')
                append("{\"id\":"); append(jsonString(text.id)); append(",\"text\":"); append(jsonString(text.text.take(160)))
                append(",\"startUs\":"); append(text.startUs); append(",\"durationUs\":"); append(text.durationUs); append('}')
            }
            append("],\"inventoryTruncated\":"); append(visual.size > 32 || p.textOverlays.size > 32); append('}')
        }
    }
}
