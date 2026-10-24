package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreviewState(
    val positionUs: Long = 0L,
    val durationUs: Long = 0L,
    val fps: Int = 30,
    val playing: Boolean = false,
    val errorMessage: String? = null,
)

data class PreviewLoadResult(val durationUs: Long, val fps: Int)

interface PreviewController {
    val state: StateFlow<PreviewState>
    fun load(project: Project): Result<Unit>
    fun play()
    fun pause()
    fun seekToUs(positionUs: Long)
    fun stepFrames(deltaFrames: Int, fps: Int = state.value.fps)
    fun setLoopRange(startUs: Long, endUs: Long?)
    fun release()
}

interface PreviewEngine {
    fun load(project: Project): Result<PreviewLoadResult>
    fun play()
    fun pause()
    fun seekToUs(positionUs: Long)
    fun currentPositionUs(): Long
    fun setErrorCallback(callback: (String) -> Unit)
    fun release()
}

interface PreviewTicker {
    fun start(onTick: () -> Unit)
    fun stop()
}

class DefaultPreviewController(
    private val engine: PreviewEngine,
    private val ticker: PreviewTicker,
) : PreviewController {
    private val transport = PreviewTransport()
    private val mutableState = MutableStateFlow(PreviewState())
    override val state: StateFlow<PreviewState> = mutableState.asStateFlow()

    private var pendingSeekTargetUs: Long? = null
    private var pendingSeekPollsRemaining = 0

    init {
        engine.setErrorCallback { message ->
            clearPendingSeek()
            mutableState.value = mutableState.value.copy(errorMessage = message, playing = false)
            transport.pause()
        }
    }

    override fun load(project: Project): Result<Unit> = loadResult(engine.load(project))

    internal fun loadResult(result: Result<PreviewLoadResult>): Result<Unit> {
        clearPendingSeek()
        return result
            .onFailure { error ->
                ticker.stop()
                transport.pause()
                mutableState.value = mutableState.value.copy(
                    playing = false,
                    errorMessage = error.message ?: "Falha ao carregar a prévia",
                )
            }
            .map { loaded ->
                transport.load(loaded.durationUs, loaded.fps)
                mutableState.value = PreviewState(durationUs = loaded.durationUs, fps = loaded.fps)
                ticker.stop()
                if (loaded.durationUs > 0L) ticker.start(::pollPosition)
            }
    }

    override fun play() {
        if (mutableState.value.errorMessage != null) return
        transport.play()
        engine.play()
        publishTransportState()
    }

    override fun pause() {
        transport.pause()
        engine.pause()
        publishTransportState()
    }

    override fun seekToUs(positionUs: Long) {
        val target = transport.seekToUs(positionUs)
        beginPendingSeek(target)
        engine.seekToUs(target)
        publishTransportState()
    }

    override fun stepFrames(deltaFrames: Int, fps: Int) {
        require(fps > 0) { "fps must be > 0" }
        if (transport.state.playing) engine.pause()
        val target = PreviewFrameMath.step(
            positionUs = transport.state.positionUs,
            deltaFrames = deltaFrames,
            fps = fps,
            durationUs = transport.state.durationUs,
        )
        transport.pause()
        transport.seekToUs(target)
        beginPendingSeek(target)
        engine.seekToUs(target)
        publishTransportState()
    }

    override fun setLoopRange(startUs: Long, endUs: Long?) {
        transport.setLoopRange(startUs, endUs)
        publishTransportState()
    }

    override fun release() {
        clearPendingSeek()
        ticker.stop()
        transport.pause()
        engine.release()
        publishTransportState()
    }

    private fun pollPosition() {
        try {
            val reportedPositionUs = engine.currentPositionUs()
            if (ignoreStalePositionWhileSeekSettles(reportedPositionUs)) {
                publishTransportState()
                return
            }

            val seekTarget = transport.onPlayerPosition(reportedPositionUs)
            if (seekTarget != null) {
                beginPendingSeek(seekTarget)
                engine.seekToUs(seekTarget)
            }
            publishTransportState()
        } catch (error: Throwable) {
            clearPendingSeek()
            ticker.stop()
            transport.pause()
            mutableState.value = mutableState.value.copy(
                playing = false,
                errorMessage = error.message ?: "Falha ao atualizar a prévia",
            )
        }
    }

    private fun beginPendingSeek(targetUs: Long) {
        pendingSeekTargetUs = targetUs.coerceIn(0L, transport.state.durationUs)
        pendingSeekPollsRemaining = SEEK_SETTLE_MAX_POLLS
    }

    private fun clearPendingSeek() {
        pendingSeekTargetUs = null
        pendingSeekPollsRemaining = 0
    }

    private fun ignoreStalePositionWhileSeekSettles(reportedPositionUs: Long): Boolean {
        val targetUs = pendingSeekTargetUs ?: return false
        val reported = reportedPositionUs.coerceIn(0L, transport.state.durationUs)
        val distanceUs = if (reported >= targetUs) reported - targetUs else targetUs - reported

        if (distanceUs <= SEEK_SETTLE_TOLERANCE_US) {
            clearPendingSeek()
            return false
        }
        if (pendingSeekPollsRemaining <= 0) {
            clearPendingSeek()
            return false
        }

        pendingSeekPollsRemaining -= 1
        return true
    }

    private fun publishTransportState() {
        val t = transport.state
        mutableState.value = mutableState.value.copy(
            positionUs = t.positionUs,
            durationUs = t.durationUs,
            fps = t.fps,
            playing = t.playing,
        )
    }

    private companion object {
        const val SEEK_SETTLE_TOLERANCE_US = 250_000L
        const val SEEK_SETTLE_MAX_POLLS = 20
    }
}
