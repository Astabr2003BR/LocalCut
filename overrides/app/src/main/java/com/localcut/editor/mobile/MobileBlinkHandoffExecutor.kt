package com.localcut.editor.mobile

import com.localcut.editor.domain.animation.PortraitMotionPresets
import com.localcut.editor.domain.history.SetClipEffectsCommand
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession

/**
 * Adds one blink effect to the existing portrait; never replaces mouth TALK,
 * images, audio or other effects. The history command restores the old clip.
 */
internal object MobileBlinkHandoffExecutor {
    data class Planned(val clipId: String, val effects: List<EffectInstance>)

    fun plan(project: Project, selectedClipId: String?, region: MobileBlinkRegion): Planned {
        val assets = project.assets.associateBy { it.id }
        val eligible = project.tracks.filter {
            it.type == TrackType.VISUAL && !it.hidden && !it.locked
        }.flatMap { it.clips }.filter { assets[it.assetId]?.type == AssetType.IMAGE }
        val image = eligible.singleOrNull()
            ?: throw IllegalArgumentException("O projeto precisa de uma única imagem visual desbloqueada")
        val selected = selectedClipId?.let { id ->
            requireNotNull(project.findClip(id)) { "Clipe selecionado não existe neste projeto" }
        }
        require(selected == null || selected.id == image.id ||
            assets[selected.assetId]?.type != AssetType.IMAGE) {
            "A imagem selecionada não é a imagem elegível"
        }
        val effects = PortraitMotionPresets.upsert(
            image.id, image.effects, region.settings(),
        )
        return Planned(image.id, effects)
    }

    fun execute(session: ProjectSession, region: MobileBlinkRegion): Result<String> = runCatching {
        val before = session.state
        val planned = plan(before.project, before.selectedClipId, region)
        val current = before.project.findClip(planned.clipId)!!
        if (current.effects == planned.effects) {
            return@runCatching "Piscadas já configuradas; nenhuma edição duplicada"
        }
        require(session.state.revision == before.revision &&
            session.state.project == before.project) { "O projeto mudou durante a solicitação" }
        session.dispatch(EditorAction.Execute(
            SetClipEffectsCommand(planned.clipId, planned.effects),
        ))
        require(session.state.revision == before.revision + 1L &&
            session.state.project.findClip(planned.clipId)?.effects == planned.effects) {
            session.state.lastError ?: "Não foi possível aplicar as piscadas"
        }
        "Piscadas aplicadas sem alterar TALK ou áudio. Desfazer disponível."
    }
}
