package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.session.ProjectSessionState

/**
 * Maps a visible image and an audible, unretimed audio clip into the exact shared
 * project-timeline interval. Does not decode media or mutate a project.
 *
 * Reverse, speed ramps and frozen audio are deliberately rejected until a
 * source-to-presentation time mapper is wired into waveform decoding.
 */
data class AudioDrivenTalkSlice(
    val bodyClipId: String,
    val audioClipId: String,
    val audioAssetId: String,
    val audioFingerprint: String,
    val visualStartOffsetUs: Long,
    val durationUs: Long,
    val audioSourceInUs: Long,
    val audioSourceOutUs: Long,
    val bucketCount: Int,
)

object AudioDrivenTalkPlanner {
    const val MAX_DURATION_US = 120_000_000L
    private const val BUCKET_SPAN_US = 25_000L

    fun prepare(project: Project, bodyClipId: String, audioClipId: String): AudioDrivenTalkSlice {
        require(bodyClipId.isNotBlank() && audioClipId.isNotBlank()) { "Selecione a imagem e o áudio" }
        val visualTrack = project.tracks.singleOrNull { track ->
            track.type == TrackType.VISUAL && track.clips.any { it.id == bodyClipId }
        } ?: throw IllegalArgumentException("Imagem não encontrada na timeline")
        require(!visualTrack.locked && !visualTrack.hidden) { "Faixa visual bloqueada ou oculta" }
        require(project.compositionGraph.nodes.none { node ->
            node.kind == CompositionNodeKind.LAYER && node.clipId == bodyClipId && node.locked
        }) { "Camada visual bloqueada" }
        val body = visualTrack.clips.single { it.id == bodyClipId }
        val imageAsset = project.assets.singleOrNull { it.id == body.assetId }
            ?: throw IllegalArgumentException("Arquivo da imagem indisponível")
        require(imageAsset.type == AssetType.IMAGE) { "TALK por região requer uma imagem estática" }

        val audioTrack = project.tracks.singleOrNull { track ->
            track.type == TrackType.AUDIO && track.clips.any { it.id == audioClipId }
        } ?: throw IllegalArgumentException("Áudio não encontrado na timeline")
        require(!audioTrack.muted && !audioTrack.hidden) { "Faixa de áudio silenciada ou oculta" }
        val anyAudioSolo = project.tracks.any { it.type == TrackType.AUDIO && it.solo }
        require(!anyAudioSolo || audioTrack.solo) { "Outra faixa de áudio está em solo" }
        val audio = audioTrack.clips.single { it.id == audioClipId }
        val audioAsset = project.assets.singleOrNull { it.id == audio.assetId }
            ?: throw IllegalArgumentException("Arquivo de áudio indisponível")
        require(audioAsset.type == AssetType.AUDIO) { "O clipe selecionado não é um áudio" }
        require(audioAsset.fingerprint.isNotBlank()) { "Identificação de áudio inválida" }
        require(audio.volume > 0.0) { "Volume do áudio está zerado" }
        require(audio.timeMap == TimeMap()) { "Áudio reverso ou com velocidade alterada ainda não é suportado" }
        require(audio.sourceInUs >= 0L && audio.sourceOutUs > audio.sourceInUs &&
            Math.subtractExact(audio.sourceOutUs, audio.sourceInUs) == audio.durationUs
        ) { "Áudio sem correspondência linear de tempo" }
        audioAsset.sourceDurationUs?.let { duration ->
            require(audio.sourceOutUs <= duration) { "Recorte do áudio excede a origem" }
        }
        val startUs = maxOf(body.startUs, audio.startUs)
        val endUs = minOf(
            Math.addExact(body.startUs, body.durationUs),
            Math.addExact(audio.startUs, audio.durationUs),
        )
        require(endUs > startUs) { "Imagem e áudio não coincidem na timeline" }
        val durationUs = endUs - startUs
        require(durationUs in 1L..MAX_DURATION_US) { "Trecho de voz excede o limite de 2 minutos" }
        val sourceInUs = Math.addExact(audio.sourceInUs, startUs - audio.startUs)
        val sourceOutUs = Math.addExact(sourceInUs, durationUs)
        require(sourceInUs >= audio.sourceInUs && sourceOutUs <= audio.sourceOutUs) {
            "Trecho de áudio inválido"
        }
        return AudioDrivenTalkSlice(
            bodyClipId = bodyClipId,
            audioClipId = audioClipId,
            audioAssetId = audioAsset.id,
            audioFingerprint = audioAsset.fingerprint,
            visualStartOffsetUs = startUs - body.startUs,
            durationUs = durationUs,
            audioSourceInUs = sourceInUs,
            audioSourceOutUs = sourceOutUs,
            bucketCount = (durationUs / BUCKET_SPAN_US + 1L).coerceIn(8L, 512L).toInt(),
        )
    }

    /** Called on the main thread immediately before a write after background decoding. */
    fun requireUnchanged(
        current: ProjectSessionState,
        expectedRevision: Long,
        expectedProjectId: String,
        expectedSlice: AudioDrivenTalkSlice,
    ) {
        require(current.project.id == expectedProjectId && current.revision == expectedRevision) {
            "O projeto mudou durante a análise; nada foi aplicado"
        }
        require(current.selectedClipId == expectedSlice.bodyClipId) {
            "A imagem selecionada mudou; nada foi aplicado"
        }
        require(prepare(current.project, expectedSlice.bodyClipId, expectedSlice.audioClipId) == expectedSlice) {
            "O áudio ou o trecho da timeline mudou; nada foi aplicado"
        }
    }

    fun plan(
        project: Project,
        slice: AudioDrivenTalkSlice,
        region: MouthRegion,
        cues: List<LipSyncCue>,
    ): CharacterAnimationPlan {
        require(cues.isNotEmpty()) { "Nenhuma atividade de voz detectada; animação não aplicada" }
        require(prepare(project, slice.bodyClipId, slice.audioClipId) == slice) {
            "Trecho de áudio ou imagem mudou"
        }
        val body = requireNotNull(project.findClip(slice.bodyClipId))
        val state = AnimationClipState(
            clipId = body.id,
            durationUs = body.durationUs,
            transform = effectiveTransform(body, slice.visualStartOffsetUs),
        )
        return CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.TALK,
                rig = CharacterRigBinding(bodyClipId = body.id),
                startOffsetUs = slice.visualStartOffsetUs,
                durationUs = slice.durationUs,
                lipSyncCues = cues,
                mouthRegion = region,
            ),
            mapOf(body.id to state),
        )
    }

    private fun effectiveTransform(clip: Clip, atOffsetUs: Long): TransformValue {
        fun scalar(propertyId: String, fallback: Double): Double {
            val animation = clip.propertyAnimations.firstOrNull { it.propertyId == propertyId }
                ?: return fallback
            if (animation.keyframes.isEmpty() || atOffsetUs < animation.keyframes.first().offsetUs) return fallback
            return (PropertyAnimationEvaluator.valueAt(animation, atOffsetUs) as? PropertyValue.Scalar)?.value
                ?: throw IllegalArgumentException("Propriedade de transformação inválida: $propertyId")
        }
        return TransformValue(
            x = scalar("transform.x", clip.transform.x),
            y = scalar("transform.y", clip.transform.y),
            scale = scalar("transform.scale", clip.transform.scale),
            rotationDeg = scalar("transform.rotationDeg", clip.transform.rotationDeg),
            opacity = scalar("transform.opacity", clip.transform.opacity),
        )
    }
}
