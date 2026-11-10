package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineValidator

/** Preview-only edit; does not mutate history until confirmed TALK is applied. */
object LocalTalkClipCoverage {
    fun projectedProject(
        project: Project,
        imageId: String,
        audioId: String,
        verifiedAudioDurationUs: Long? = null,
    ): Project {
        require(verifiedAudioDurationUs == null ||
            verifiedAudioDurationUs in 1L..AudioDrivenTalkPlanner.MAX_DURATION_US) {
            "Duração verificada do áudio fora do limite"
        }
        val imageTrack = project.tracks.singleOrNull { track ->
            track.type == TrackType.VISUAL && track.clips.any { it.id == imageId }
        } ?: throw IllegalArgumentException("Imagem visual não encontrada")
        require(!imageTrack.locked && !imageTrack.hidden) { "Faixa visual bloqueada" }
        val image = imageTrack.clips.single { it.id == imageId }
        val imageAsset = project.assets.singleOrNull { it.id == image.assetId }
        require(imageAsset?.type == AssetType.IMAGE) { "Expansão automática exige uma imagem estática" }
        val audioTrack = project.tracks.singleOrNull { track ->
            track.type == TrackType.AUDIO && track.clips.any { it.id == audioId }
        } ?: throw IllegalArgumentException("Áudio não encontrado")
        val audio = audioTrack.clips.single { it.id == audioId }
        val audioAsset = project.assets.firstOrNull { it.id == audio.assetId }
        require(audioAsset?.type == AssetType.AUDIO) { "Voz inválida" }

        // A newly imported audio clip can default to five seconds even when its
        // local source contains a longer voice recording. Only the exact untrimmed
        // 0..5s source window qualifies for this confirmed recovery.
        // The original importer may have kept the 5-second default even though
        // the private MP3 is longer. A duration measured locally from the same
        // owned source can repair that metadata, but never reduce a known length.
        val sourceDuration = maxOf(
            audioAsset.sourceDurationUs ?: 0L,
            verifiedAudioDurationUs ?: 0L,
        ).takeIf { it > 0L }
        val recoverImportedVoice = sourceDuration != null &&
            sourceDuration > 5_000_000L &&
            sourceDuration <= AudioDrivenTalkPlanner.MAX_DURATION_US &&
            audio.startUs == image.startUs &&
            audio.sourceInUs == 0L &&
            audio.sourceOutUs == 5_000_000L &&
            audio.durationUs == 5_000_000L &&
            audio.timeMap == TimeMap()
        if (recoverImportedVoice) {
            require(!audioTrack.locked && !audioTrack.hidden) {
                "A faixa de voz está bloqueada ou oculta; não é possível estendê-la"
            }
        }
        val plannedAudio = if (recoverImportedVoice) {
            audio.copy(durationUs = requireNotNull(sourceDuration), sourceOutUs = sourceDuration)
        } else audio
        val targetEndUs = Math.addExact(plannedAudio.startUs, plannedAudio.durationUs)
        val imageEndUs = Math.addExact(image.startUs, image.durationUs)
        if (targetEndUs <= imageEndUs && plannedAudio == audio) return project
        // Extending when the starts differ can change the user's intended edit.
        require(image.startUs == audio.startUs) {
            "Para estender automaticamente, imagem e voz devem começar no mesmo ponto"
        }
        require(audio.durationUs in 1L..AudioDrivenTalkPlanner.MAX_DURATION_US) { "Voz fora do limite" }
        val extendImage = targetEndUs > imageEndUs
        if (extendImage) {
            require(image.timeMap == TimeMap()) { "Imagem com velocidade alterada: ajuste manualmente" }
        }
        val newDurationUs = if (extendImage) Math.subtractExact(targetEndUs, image.startUs) else image.durationUs
        val newSourceOutUs = if (extendImage) Math.addExact(image.sourceInUs, newDurationUs)
            else image.sourceOutUs
        require(!extendImage || imageAsset.sourceDurationUs == null ||
            newSourceOutUs <= imageAsset.sourceDurationUs) {
            "A origem da imagem não permite essa duração"
        }
        val tracks = project.tracks.map { track ->
            when (track.id) {
                imageTrack.id -> track.copy(clips = track.clips.map { clip ->
                    if (clip.id != imageId || !extendImage) clip else clip.copy(
                        durationUs = newDurationUs, sourceOutUs = newSourceOutUs,
                    )
                })
                audioTrack.id -> track.copy(clips = track.clips.map { clip ->
                    if (clip.id != audioId) clip else plannedAudio
                })
                else -> track
            }
        }
        val correctedAssets = if (
            recoverImportedVoice && sourceDuration != audioAsset.sourceDurationUs
        ) project.assets.map { asset ->
            if (asset.id == audioAsset.id) asset.copy(sourceDurationUs = sourceDuration) else asset
        } else project.assets
        val extended = project.copy(assets = correctedAssets, tracks = tracks)
        val issues = TimelineValidator.validate(extended)
        require(issues.isEmpty()) {
            "Não foi possível estender a imagem sem conflito: ${issues.firstOrNull()}"
        }
        return extended
    }
}
