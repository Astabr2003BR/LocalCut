package com.localcut.editor.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.ui.PlayerView
import com.localcut.editor.bridge.agent.AndroidPlayerPreviewFramePort
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath
import java.io.File
import java.lang.ref.WeakReference

@UnstableApi
@OptIn(ExperimentalApi::class)
class Media3PreviewController(
    context: Context,
    compositionCompiler: ProjectCompositionCompiler,
    runtimeProvider: PreviewRuntimeProvider = AndroidPreviewRuntimeProvider(context),
) : PreviewController {
    private val engine = Media3PreviewEngine(context, compositionCompiler, runtimeProvider)
    private val delegate = DefaultPreviewController(engine, AndroidPreviewTicker())
    private val backgroundSession = PreviewBackgroundSession()
    private var lastProject: Project? = null
    @Volatile
    private var boundViewRef: WeakReference<PlayerView>? = null
    val bridgePreviewFramePort = AndroidPlayerPreviewFramePort(
        positionUsProvider = { state.value.positionUs },
    )

    override val state = delegate.state
    override fun load(project: Project): Result<Unit> = delegate.load(project).also { result ->
        if (result.isSuccess) {
            lastProject = project
        }
        scheduleBoundSurfaceRefreshAfterContentLoad(result.isSuccess)
    }

    fun loadRenderedFile(file: File, durationUs: Long, fps: Int): Result<Unit> {
        val editorProject = lastProject
        val editorPositionUs = state.value.positionUs
        val result = delegate.loadResult(engine.loadRenderedFile(file, durationUs, fps))
        if (result.isSuccess) {
            scheduleBoundSurfaceRefreshAfterContentLoad(loadSucceeded = true)
        } else if (editorProject != null) {
            FinalPreviewRecovery.restore(
                controller = this,
                project = editorProject,
                positionUs = editorPositionUs,
                durationUs = TimelineMath.durationUs(editorProject),
            )
            bridgePreviewFramePort.requestRefresh()
        }
        return result
    }

    override fun play() = delegate.play()
    override fun pause() = delegate.pause()
    override fun seekToUs(positionUs: Long) {
        delegate.seekToUs(positionUs)
        bridgePreviewFramePort.requestRefresh()
    }
    override fun stepFrames(deltaFrames: Int, fps: Int) {
        delegate.stepFrames(deltaFrames, fps)
        bridgePreviewFramePort.requestRefresh()
    }
    override fun setLoopRange(startUs: Long, endUs: Long?) = delegate.setLoopRange(startUs, endUs)
    override fun release() = delegate.release()

    /**
     * Releases the Activity-owned Media3 player while the UI is backgrounded. The project model,
     * history and Bridge runtime remain process-resident; only preview codecs/surfaces are dropped.
     */
    fun suspendForBackground(changingConfigurations: Boolean) {
        if (!PreviewBackgroundMemoryPolicy.shouldSuspend(state.value.durationUs, changingConfigurations)) return
        if (!backgroundSession.beginSuspend(state.value.positionUs)) return

        delegate.suspendForBackground()
        boundViewRef?.get()?.let { view ->
            bridgePreviewFramePort.detach(view)
            view.player = null
        }
        engine.releaseForBackground()
    }

    /** Recreates the preview player after background suspension and restores the previous position. */
    fun resumeAfterBackground() {
        val resumePositionUs = backgroundSession.takeResumePositionUs() ?: return
        val project = lastProject
        if (project == null) {
            delegate.resumeAfterBackground()
            return
        }

        val loaded = load(project)
        if (loaded.isSuccess) {
            seekToUs(resumePositionUs.coerceAtMost(state.value.durationUs))
        }
    }

    fun bindTo(view: PlayerView) {
        boundViewRef = WeakReference(view)
        assignPlayerIfNeeded(view)
        view.useController = false
        bridgePreviewFramePort.attach(view)
    }

    fun refreshBoundView(view: PlayerView) {
        val positionUs = state.value.positionUs
        val player = engine.player
        boundViewRef = WeakReference(view)
        assignPlayerIfNeeded(view)
        view.useController = false
        bridgePreviewFramePort.attach(view)

        val videoOutput = view.videoSurfaceView
        val surfaceReady = when (videoOutput) {
            is SurfaceView -> videoOutput.holder.surface.isValid
            is TextureView -> videoOutput.isAvailable
            else -> false
        }
        if (
            PreviewSurfaceRecoveryPolicy.shouldRebindVideoOutput(
                durationUs = state.value.durationUs,
                hasBoundView = boundViewRef?.get() === view,
                surfaceReady = surfaceReady,
            )
        ) {
            // PlayerView owns the normal Media3 surface lifecycle. Re-register only after the
            // recreated surface is actually ready; returning from Home/pickers can briefly leave
            // SurfaceView/TextureView present but not usable yet on Android 16.
            runCatching {
                when (videoOutput) {
                    is SurfaceView -> player.setVideoSurfaceView(videoOutput)
                    is TextureView -> player.setVideoTextureView(videoOutput)
                }
            }
        }

        delegate.seekToUs(positionUs)
        bridgePreviewFramePort.requestRefresh()
    }

    fun unbindFrom(view: PlayerView) {
        if (boundViewRef?.get() === view) {
            boundViewRef = null
        }
        bridgePreviewFramePort.detach(view)
        if (view.player === engine.currentPlayerOrNull()) view.player = null
    }

    private fun assignPlayerIfNeeded(view: PlayerView) {
        val player = engine.player
        if (
            PreviewPlayerViewPolicy.shouldAssignPreviewPlayer(
                alreadyBoundToPreviewPlayer = view.player === player,
            )
        ) {
            view.player = player
        }
    }

    private fun scheduleBoundSurfaceRefreshAfterContentLoad(loadSucceeded: Boolean) {
        val view = boundViewRef?.get()
        if (!PreviewPlayerViewPolicy.shouldRefreshAfterContentLoad(
                loadSucceeded = loadSucceeded,
                durationUs = state.value.durationUs,
                hasBoundView = view != null,
            )
        ) {
            if (loadSucceeded) bridgePreviewFramePort.requestRefresh()
            return
        }

        checkNotNull(view).post {
            if (boundViewRef?.get() !== view || state.value.durationUs <= 0L) return@post
            refreshBoundView(view)
        }
    }
}

@UnstableApi
@OptIn(ExperimentalApi::class)
private class Media3PreviewEngine(
    context: Context,
    private val compiler: ProjectCompositionCompiler,
    private val runtimeProvider: PreviewRuntimeProvider,
) : PreviewEngine {
    private val appContext = context.applicationContext
    private var activePlayer: CompositionPlayer? = null
    private var errorCallback: (String) -> Unit = {}

    val player: CompositionPlayer
        get() = ensurePlayer()

    init {
        ensurePlayer()
    }

    fun currentPlayerOrNull(): CompositionPlayer? = activePlayer

    fun releaseForBackground() {
        activePlayer?.release()
        activePlayer = null
    }

    override fun load(project: Project): Result<PreviewLoadResult> {
        val player = ensurePlayer()
        val durationUs = TimelineMath.durationUs(project)
        if (durationUs == 0L) {
            player.pause()
            player.stop()
            return Result.success(PreviewLoadResult(0L, project.fps))
        }
        val outputSettings = PreviewRenderSettingsResolver.resolve(
            project = project,
            runtime = runtimeProvider.current(),
        ).outputSettings
        return compiler.compile(
            project = project,
            mode = RenderMode.FAST_PREVIEW,
            outputSettings = outputSettings,
        ).map { composition ->
            player.setComposition(composition)
            player.prepare()
            PreviewLoadResult(durationUs, project.fps)
        }
    }

    fun loadRenderedFile(file: File, durationUs: Long, fps: Int): Result<PreviewLoadResult> = runCatching {
        require(file.isFile && file.length() > 0L) { "Rendered preview file is missing or empty" }
        require(durationUs > 0L) { "Rendered preview duration must be > 0" }
        require(fps > 0) { "Rendered preview fps must be > 0" }
        val player = ensurePlayer()
        val mediaItem = MediaItem.Builder().setUri(file.toURI().toString()).build()
        val edited = EditedMediaItem.Builder(mediaItem).setDurationUs(durationUs).build()
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
        player.setComposition(Composition.Builder(sequence).build())
        player.prepare()
        PreviewLoadResult(durationUs, fps)
    }

    override fun play() = ensurePlayer().play()
    override fun pause() {
        activePlayer?.pause()
    }
    override fun seekToUs(positionUs: Long) = ensurePlayer().seekTo(positionUs.coerceAtLeast(0L) / 1_000L)
    override fun currentPositionUs(): Long = (activePlayer?.currentPosition ?: 0L) * 1_000L
    override fun setErrorCallback(callback: (String) -> Unit) {
        errorCallback = callback
    }
    override fun release() = releaseForBackground()

    private fun ensurePlayer(): CompositionPlayer {
        activePlayer?.let { return it }
        return CompositionPlayer.Builder(appContext)
            .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorCallback(error.message ?: "Media3 playback error")
                    }
                })
                activePlayer = player
            }
    }
}

private class AndroidPreviewTicker(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : PreviewTicker {
    private var callback: (() -> Unit)? = null
    private val runnable = object : Runnable {
        override fun run() {
            callback?.invoke()
            if (callback != null) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun start(onTick: () -> Unit) {
        stop()
        callback = onTick
        handler.post(runnable)
    }

    override fun stop() {
        callback = null
        handler.removeCallbacks(runnable)
    }

    private companion object { const val TICK_MS = 33L }
}
