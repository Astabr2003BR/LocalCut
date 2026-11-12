package com.localcut.editor.mobile

import android.content.Context
import androidx.media3.common.util.UnstableApi
import android.os.Handler
import android.os.Looper
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.audio.AndroidWaveformDecoder
import com.localcut.editor.audio.WaveformCache
import com.localcut.editor.audio.WaveformCacheKey
import com.localcut.editor.audio.WaveformRepository
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.animation.AudioDrivenTalkPlanner
import com.localcut.editor.domain.animation.MouthRegion
import com.localcut.editor.domain.animation.LocalTalkClipCoverage
import com.localcut.editor.domain.animation.WaveformLipSyncCueCoordinator
import com.localcut.editor.domain.history.ApplyConfirmedTalkCommand
import com.localcut.editor.domain.history.ApplyAnimationPlanCommand
import com.localcut.editor.session.EditorAction
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes the same offline TALK plan as AudioLipSyncActionControl, but only after
 * MobileHandoffActivity's explicit approval. No remote media or server-side voices.
 * Owns its two threads and cancels on activity destruction or timeout.
 */
@UnstableApi
class ConfirmedMobileLipSyncExecutor(
    context: Context,
    private val runtime: LocalCutProcessRuntime,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val decoderExecutor = Executors.newSingleThreadExecutor()
    private val plannerExecutor = Executors.newSingleThreadExecutor()
    private val coordinator = WaveformLipSyncCueCoordinator(
        WaveformRepository(
            WaveformCache(maxSamples = 8_192),
            AndroidWaveformDecoder(context.contentResolver),
            decoderExecutor,
        ),
        plannerExecutor,
    )
    private val resolver = AssetUriResolver(context.contentResolver)
    private val audioDurationProbe = AndroidAudioSourceDurationProbe(context.applicationContext)
    private var active: WaveformLipSyncCueCoordinator.RequestHandle? = null
    private val finished = AtomicBoolean(false)
    private var deadline: Runnable? = null

    fun start(region: MouthRegion, callback: (Result<String>) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "TALK deve começar na thread principal" }
        if (finished.get()) return
        val initial = runCatching {
            val previous = runtime.session.state
            val selected = MobileLipSyncSelection.resolve(previous.project, previous.selectedClipId)
            runtime.session.dispatch(EditorAction.SelectClip(selected.imageClipId))
            val state = runtime.session.state
            require(state.selectedClipId == selected.imageClipId) { "Imagem não selecionada" }
            val audioClip = requireNotNull(state.project.findClip(selected.audioClipId))
            val asset = requireNotNull(state.project.assets.firstOrNull { it.id == audioClip.assetId })
            val uri = resolver.resolve(asset).getOrThrow().toString()
            require(uri.startsWith("content://") || uri.startsWith("file://")) {
                "Fonte de áudio não está no armazenamento local"
            }
            Initial(state, selected.imageClipId, selected.audioClipId, uri)
        }.getOrElse {
            complete(Result.failure(it), callback)
            return
        }
        val timeout = Runnable {
            if (!finished.get()) {
                active?.cancel()
                complete(Result.failure(IllegalStateException("Tempo limite de análise de áudio")), callback)
            }
        }
        deadline = timeout
        handler.postDelayed(timeout, 60_000L)
        try {
            // MediaMetadataRetriever is native I/O. Never block the Android main
            // thread while probing the actual duration of a LocalCut-owned MP3.
            decoderExecutor.execute {
                val verifiedDurationUs = audioDurationProbe.readDurationUs(initial.uri)
                handler.post {
                    if (finished.get()) return@post
                    startWaveform(initial, verifiedDurationUs, region, callback)
                }
            }
        } catch (error: Throwable) {
            complete(Result.failure(error), callback)
        }
    }

    private fun startWaveform(
        initial: Initial,
        verifiedDurationUs: Long?,
        region: MouthRegion,
        callback: (Result<String>) -> Unit,
    ) {
        val prepared = runCatching {
            val state = runtime.session.state
            require(state.revision == initial.state.revision &&
                state.project == initial.state.project &&
                state.selectedClipId == initial.imageClipId) {
                "O projeto mudou durante a leitura do áudio; TALK cancelado"
            }
            val projected = LocalTalkClipCoverage.projectedProject(
                state.project, initial.imageClipId, initial.audioClipId,
                verifiedAudioDurationUs = verifiedDurationUs,
            )
            val slice = AudioDrivenTalkPlanner.prepare(
                projected, initial.imageClipId, initial.audioClipId,
            )
            Prepared(state, projected, slice, initial.uri, verifiedDurationUs)
        }.getOrElse {
            complete(Result.failure(it), callback)
            return
        }
        val (state, projected, slice, uri, verifiedDurationUsForCommit) = prepared
        val key = WaveformCacheKey(
            slice.audioFingerprint, slice.audioSourceInUs,
            slice.audioSourceOutUs, slice.bucketCount,
        )
        try {
            active = coordinator.request(key, uri, slice.durationUs) { decoded ->
                handler.post {
                    if (finished.get()) return@post
                    val applied = runCatching {
                        val current = runtime.session.state
                        require(current.revision == state.revision &&
                            current.project.id == state.project.id &&
                            current.selectedClipId == slice.bodyClipId &&
                            current.project == state.project
                        ) { "O projeto mudou durante a análise; TALK cancelado" }
                        val refreshedProjection = LocalTalkClipCoverage.projectedProject(
                            current.project, slice.bodyClipId, slice.audioClipId,
                            verifiedAudioDurationUs = verifiedDurationUsForCommit,
                        )
                        require(refreshedProjection == projected &&
                            AudioDrivenTalkPlanner.prepare(
                                refreshedProjection, slice.bodyClipId, slice.audioClipId,
                            ) == slice
                        ) { "Trecho da timeline mudou; TALK cancelado" }
                        val plan = AudioDrivenTalkPlanner.plan(
                            projected, slice, region, decoded.getOrThrow(),
                        )
                        val nextProject = ApplyAnimationPlanCommand(plan).apply(projected).project
                        if (nextProject == current.project) {
                            "TALK já estava configurado exatamente assim; nenhuma edição foi feita. " +
                                "Confira a prévia e ajuste a região da boca se não houver movimento."
                        } else {
                            runtime.session.dispatch(EditorAction.Execute(ApplyConfirmedTalkCommand(
                                slice.bodyClipId, slice.audioClipId, plan,
                                verifiedAudioDurationUs = verifiedDurationUsForCommit,
                            )))
                            check(runtime.session.state.revision == state.revision + 1L) {
                                runtime.session.state.lastError ?: "Não foi possível aplicar TALK"
                            }
                            runtime.editorViewModel.refreshSnapshot()
                            "TALK aplicado por ${slice.durationUs / 1_000_000.0} segundos; " +
                                "confira a boca na prévia. Desfazer disponível."
                        }
                    }
                    complete(applied, callback)
                }
            }
        } catch (error: Throwable) {
            complete(Result.failure(error), callback)
        }
    }

    private data class Initial(
        val state: com.localcut.editor.session.ProjectSessionState,
        val imageClipId: String,
        val audioClipId: String,
        val uri: String,
    )

    private data class Prepared(
        val state: com.localcut.editor.session.ProjectSessionState,
        val projected: com.localcut.editor.domain.model.Project,
        val slice: com.localcut.editor.domain.animation.AudioDrivenTalkSlice,
        val uri: String,
        val verifiedSourceDurationUs: Long?,
    )

    private fun complete(result: Result<String>, callback: (Result<String>) -> Unit) {
        if (!finished.compareAndSet(false, true)) return
        deadline?.let { handler.removeCallbacks(it) }
        active?.cancel()
        decoderExecutor.shutdownNow()
        plannerExecutor.shutdownNow()
        callback(result)
    }

    fun close() {
        if (!finished.compareAndSet(false, true)) return
        deadline?.let { handler.removeCallbacks(it) }
        active?.cancel()
        decoderExecutor.shutdownNow()
        plannerExecutor.shutdownNow()
    }
}
