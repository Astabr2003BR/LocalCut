package com.localcut.editor.mobile

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType

/** Never guess a clip ID if more than one candidate exists. */
object MobileLipSyncSelection {
    data class Selection(val imageClipId: String, val audioClipId: String)

    fun resolve(project: Project, selectedClipId: String?): Selection {
        val assets = project.assets.associateBy { it.id }
        val images = project.tracks.filter { it.type == TrackType.VISUAL && !it.hidden && !it.locked }
            .flatMap { it.clips }
            .filter { assets[it.assetId]?.type == AssetType.IMAGE }
        val audio = project.tracks.filter { it.type == TrackType.AUDIO && !it.hidden && !it.muted }
            .flatMap { it.clips }
            .filter { assets[it.assetId]?.type == AssetType.AUDIO }
        val image = images.singleOrNull()
            ?: throw IllegalArgumentException("Selecione um projeto com uma única imagem visual desbloqueada")
        val voice = audio.singleOrNull()
            ?: throw IllegalArgumentException("Selecione um projeto com uma única voz não silenciada")
        // An audio selection is common after importing; it must not prevent a
        // unique-image lip-sync. A different image selection is ambiguous.
        require(selectedClipId == null || selectedClipId == image.id ||
            project.findClip(selectedClipId)?.let { assets[it.assetId]?.type } != AssetType.IMAGE
        ) { "Imagem selecionada não corresponde à imagem elegível" }
        require(image.id != voice.id) { "Imagem e voz não podem usar o mesmo clipe" }
        return Selection(image.id, voice.id)
    }
}
