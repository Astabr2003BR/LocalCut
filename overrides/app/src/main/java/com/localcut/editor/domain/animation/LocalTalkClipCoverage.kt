package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineValidator

/** Preview-only edit; does not mutate history until confirmed TALK is applied. */
object LocalTalkClipCoverage {
    fun projectedProject(project: Project, imageId: String, audioId: String): Project {
        val imageTrack = project.tracks.singleOrNull { track ->
            track.type == TrackType.VISUAL && track.clips.any { it.id == imageId }
        } ?: throw IllegalArgumentException("Imagem visual não encontrada")
        require(!imageTrack.locked && !imageTrack.hidden) { "Faixa visual bloqueada" }
        val image = imageTrack.clips.single { it.id == imageId }
        val imageAsset = project.assets.singleOrNull { it.id == image.assetId }
        require(imageAsset?.type == AssetType.IMAGE) { "Expansão automática exige uma imagem estática" }
        val audio = project.tracks.filter { it.type == TrackType.AUDIO }
            .flatMap { it.clips }.singleOrNull { it.id == audioId }
            ?: throw IllegalArgumentException("Áudio não encontrado")
        val audioAsset = project.assets.firstOrNull { it.id == audio.assetId }
        require(audioAsset?.type == AssetType.AUDIO) { "Voz inválida" }
        val targetEndUs = Math.addExact(audio.startUs, audio.durationUs)
        val imageEndUs = Math.addExact(image.startUs, image.durationUs)
        if (targetEndUs <= imageEndUs) return project
        // Extending when the starts differ can change the user's intended edit.
        require(image.startUs == audio.startUs) {
            "Para estender automaticamente, imagem e voz devem começar no mesmo ponto"
        }
        require(audio.durationUs in 1L..AudioDrivenTalkPlanner.MAX_DURATION_US) { "Voz fora do limite" }
        require(image.timeMap == TimeMap()) { "Imagem com velocidade alterada: ajuste manualmente" }
        val newDurationUs = Math.subtractExact(targetEndUs, image.startUs)
        val newSourceOutUs = Math.addExact(image.sourceInUs, newDurationUs)
        require(imageAsset.sourceDurationUs == null || newSourceOutUs <= imageAsset.sourceDurationUs) {
            "A origem da imagem não permite essa duração"
        }
        val tracks = project.tracks.map { track ->
            if (track.id != imageTrack.id) return@map track
            track.copy(clips = track.clips.map { clip ->
                if (clip.id != imageId) clip else clip.copy(
                    durationUs = newDurationUs, sourceOutUs = newSourceOutUs,
                )
            })
        }
        val extended = project.copy(tracks = tracks)
        val issues = TimelineValidator.validate(extended)
        require(issues.isEmpty()) {
            "Não foi possível estender a imagem sem conflito: ${issues.firstOrNull()}"
        }
        return extended
    }
}
