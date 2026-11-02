package com.localcut.editor.media

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.*

private var passed = 0
private var failed = 0
private fun case(name: String, block: () -> Unit) { try { block(); passed++; println("PASS $name") } catch (t: Throwable) { failed++; println("FAIL $name: ${t.message}") } }
private fun assertThat(v: Boolean, m: String) { if (!v) error(m) }

private class FakeEngine : PreviewEngine {
    var loadCount = 0
    var playCount = 0
    var pauseCount = 0
    var seekPosition = 0L
    var position = 0L
    var applySeekImmediately = true
    var released = false
    var positionFailure: Throwable? = null
    var errorListener: ((String) -> Unit)? = null
    override fun load(project: Project): Result<PreviewLoadResult> {
        loadCount++
        return Result.success(
            PreviewLoadResult(
                durationUs = project.tracks.flatMap { it.clips }.maxOfOrNull { it.startUs + it.durationUs } ?: 0L,
                fps = project.fps,
                outputWidth = project.width,
                outputHeight = project.height,
            ),
        )
    }
    override fun play() { playCount++ }
    override fun pause() { pauseCount++ }
    override fun seekToUs(positionUs: Long) {
        seekPosition = positionUs
        if (applySeekImmediately) position = positionUs
    }
    override fun currentPositionUs(): Long { positionFailure?.let { throw it }; return position }
    override fun setErrorCallback(callback: (String) -> Unit) { errorListener = callback }
    override fun release() { released = true }
}

private class FakeTicker : PreviewTicker {
    var callback: (() -> Unit)? = null
    override fun start(onTick: () -> Unit) { callback = onTick }
    override fun stop() { callback = null }
    fun tick() { callback?.invoke() }
}

private fun project(durationUs: Long = 5_000_000L): Project {
    val asset = Asset("img", AssetType.IMAGE, "content://img", null, 1, "a.png", "image/png", "f")
    return Project(
        "p", BuildContract.PROJECT_SCHEMA_VERSION, "P", 1080, 1920, 30, listOf(asset),
        listOf(
            Track("visual-1", TrackType.VISUAL, 0, clips = listOf(Clip("c", "img", 0, durationUs, 0, durationUs))),
            Track("audio-1", TrackType.AUDIO, 1)
        )
    )
}

fun main() {
    case("load exposes duration fps and exact render geometry") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(project()).getOrThrow()
        assertThat(controller.state.value.durationUs == 5_000_000L, "duration")
        assertThat(controller.state.value.fps == 30, "fps")
        assertThat(controller.state.value.outputWidth == 1080, "output width")
        assertThat(controller.state.value.outputHeight == 1920, "output height")
    }
    case("play pause and position polling") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(project()).getOrThrow(); controller.play(); engine.position = 1_200_000; ticker.tick()
        assertThat(controller.state.value.playing && controller.state.value.positionUs == 1_200_000L, "playing position")
        controller.pause(); assertThat(!controller.state.value.playing && engine.pauseCount == 1, "pause")
    }
    case("seek and frame step clamp") {
        val engine = FakeEngine(); val controller = DefaultPreviewController(engine, FakeTicker())
        controller.load(project()).getOrThrow(); controller.seekToUs(4_990_000); controller.stepFrames(1, 30)
        assertThat(engine.seekPosition == 5_000_000L, "frame clamp")
        controller.stepFrames(-1, 30); assertThat(engine.seekPosition < 5_000_000L, "step back")
    }
    case("stale player poll cannot overwrite a pending backward seek") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(project()).getOrThrow()
        engine.position = 4_500_000L
        engine.applySeekImmediately = false

        controller.seekToUs(500_000L)
        assertThat(controller.state.value.positionUs == 500_000L, "seek must publish requested position immediately")

        ticker.tick()
        assertThat(controller.state.value.positionUs == 500_000L, "stale pre-seek position must be ignored while seek settles")

        engine.position = 520_000L
        ticker.tick()
        assertThat(controller.state.value.positionUs == 520_000L, "player position must resume once it reaches seek target")
    }
    case("loop seeks to start after crossing end") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(project()).getOrThrow(); controller.setLoopRange(1_000_000, 2_000_000); controller.play()
        engine.position = 2_100_000; ticker.tick()
        assertThat(engine.seekPosition == 1_000_000L, "loop seek")
    }
    case("reload replaces composition instead of appending") {
        val engine = FakeEngine(); val controller = DefaultPreviewController(engine, FakeTicker())
        controller.load(project()).getOrThrow(); controller.load(project(3_000_000)).getOrThrow()
        assertThat(engine.loadCount == 2, "load count")
        assertThat(controller.state.value.durationUs == 3_000_000L, "new duration")
    }
    case("rendered preview load resets transport and clears unknown geometry") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(project()).getOrThrow(); controller.seekToUs(2_000_000L)
        controller.loadResult(Result.success(PreviewLoadResult(durationUs = 1_000_000L, fps = 24))).getOrThrow()
        assertThat(engine.loadCount == 1, "project engine must not reload")
        assertThat(controller.state.value.durationUs == 1_000_000L, "rendered duration")
        assertThat(controller.state.value.positionUs == 0L && controller.state.value.fps == 24, "rendered state reset")
        assertThat(controller.state.value.outputWidth == null && controller.state.value.outputHeight == null, "rendered geometry must be unknown")
    }
    case("engine error becomes visible state") {
        val engine = FakeEngine(); val controller = DefaultPreviewController(engine, FakeTicker())
        controller.load(project()).getOrThrow(); engine.errorListener?.invoke("decode failed")
        assertThat(controller.state.value.errorMessage == "decode failed", "error state")
    }
    case("release stops ticker and engine") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(project()).getOrThrow(); controller.release()
        assertThat(engine.released && ticker.callback == null, "release")
    }
    case("empty project does not start polling") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(Project.create("Empty", 1080, 1920, 30)).getOrThrow()
        assertThat(ticker.callback == null, "empty project must not poll player position")
    }
    case("polling failure becomes state instead of escaping") {
        val engine = FakeEngine(); val ticker = FakeTicker(); val controller = DefaultPreviewController(engine, ticker)
        controller.load(project()).getOrThrow()
        engine.positionFailure = IllegalStateException("player not ready")
        ticker.tick()
        assertThat(controller.state.value.errorMessage?.contains("player not ready") == true, "poll error state")
        assertThat(ticker.callback == null, "polling should stop after failure")
    }
    println("RESULT passed=$passed failed=$failed")
    if (failed != 0) error("failed=$failed")
}
