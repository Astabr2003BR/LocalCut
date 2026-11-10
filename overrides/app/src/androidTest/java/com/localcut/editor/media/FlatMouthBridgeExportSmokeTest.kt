package com.localcut.editor.media

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.bridge.agent.BridgeCommandExecutor
import com.localcut.editor.bridge.agent.BridgePlaybackPort
import com.localcut.editor.audio.AndroidWaveformDecoder
import com.localcut.editor.audio.WaveformCache
import com.localcut.editor.audio.WaveformCacheKey
import com.localcut.editor.audio.WaveformRepository
import com.localcut.editor.domain.animation.AudioDrivenTalkPlanner
import com.localcut.editor.domain.animation.LipSyncCue
import com.localcut.editor.domain.animation.MouthRegion
import com.localcut.editor.ui.editor.MouthRegionPlacementPolicy
import com.localcut.editor.ui.editor.MouthRegionRenderProjectionPolicy
import com.localcut.editor.ui.editor.loadPreviewBitmap
import com.localcut.editor.domain.animation.WaveformLipSyncCueCoordinator
import com.localcut.editor.domain.history.ApplyAnimationPlanCommand
import com.localcut.editor.session.EditorAction
import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.Cancellable
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.DelayScheduler
import com.localcut.editor.session.InMemoryProjectRevisionClock
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class FlatMouthBridgeExportSmokeTest {
    @Test
    fun eyeBlinkAndTalkProduceVisibleExportedFramesAndKeepAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val image = createFacePng(File(directory, "eyes-${System.nanoTime()}.png"))
        val audio = createVoiceWav(File(directory, "eyes-voice-${System.nanoTime()}.wav"))
        val faceAsset = imageAsset(context.packageName, image)
        val voiceAsset = Asset(
            id = "eyes-voice-asset", type = AssetType.AUDIO,
            uri = FileProvider.getUriForFile(context,
                "${context.packageName}.remote-media", audio).toString(),
            sourceDurationUs = DURATION_US, sizeBytes = audio.length(),
            displayName = audio.name, mimeType = "audio/wav",
            fingerprint = "eyes-wav-${audio.length()}",
        )
        val blink = com.localcut.editor.domain.model.EffectInstance(
            id = "eye-blink", typeId = LocalCutEyeBlinkEffect.TYPE_ID,
            parameters = mapOf(
                "leftEye" to com.localcut.editor.domain.model.EffectParameter.Point(0.40, 0.42),
                "rightEye" to com.localcut.editor.domain.model.EffectParameter.Point(0.60, 0.42),
                "radiusX" to com.localcut.editor.domain.model.EffectParameter.Number(0.07),
                "radiusY" to com.localcut.editor.domain.model.EffectParameter.Number(0.085),
                "firstBlinkSeconds" to com.localcut.editor.domain.model.EffectParameter.Number(0.30),
                "intervalSeconds" to com.localcut.editor.domain.model.EffectParameter.Number(3.20),
                "durationSeconds" to com.localcut.editor.domain.model.EffectParameter.Number(0.18),
                "blinkStrength" to com.localcut.editor.domain.model.EffectParameter.Number(1.0),
                "swayStrength" to com.localcut.editor.domain.model.EffectParameter.Number(0.25),
                "breathStrength" to com.localcut.editor.domain.model.EffectParameter.Number(0.40),
            ),
        )
        val mouth = com.localcut.editor.domain.model.EffectInstance(
            id = "talk-existing", typeId = LocalCutMouthWarpEffect.TYPE_ID,
            parameters = mapOf(
                "center" to com.localcut.editor.domain.model.EffectParameter.Point(0.50, 0.68),
                "radiusX" to com.localcut.editor.domain.model.EffectParameter.Number(0.14),
                "radiusY" to com.localcut.editor.domain.model.EffectParameter.Number(0.08),
                "openness" to com.localcut.editor.domain.model.EffectParameter.Number(0.0),
            ),
            animations = listOf(com.localcut.editor.domain.model.PropertyAnimation(
                propertyId = "openness", keyframes = listOf(
                    com.localcut.editor.domain.model.PropertyKeyframe(
                        "talk-start", 0L, com.localcut.editor.domain.model.PropertyValue.Scalar(0.0)),
                    com.localcut.editor.domain.model.PropertyKeyframe(
                        "talk-open", 600_000L, com.localcut.editor.domain.model.PropertyValue.Scalar(0.75)),
                    com.localcut.editor.domain.model.PropertyKeyframe(
                        "talk-close", DURATION_US, com.localcut.editor.domain.model.PropertyValue.Scalar(0.0)),
                ),
            )),
        )
        val seed = Project.create("blink-with-talk", WIDTH, HEIGHT, 30)
        val project = seed.copy(
            assets = listOf(faceAsset, voiceAsset),
            tracks = seed.tracks.map { track ->
                when (track.type) {
                    TrackType.VISUAL -> track.copy(clips = listOf(Clip(
                        BODY_CLIP_ID, faceAsset.id, 0L, DURATION_US, 0L, DURATION_US,
                        effects = listOf(mouth, blink),
                    )))
                    TrackType.AUDIO -> track.copy(clips = listOf(Clip(
                        "eyes-voice", voiceAsset.id, 0L, DURATION_US, 0L, DURATION_US,
                    )))
                }
            },
        )
        val visualEffects = project.findClip(BODY_CLIP_ID)!!.effects
        assertEquals(2, visualEffects.size)
        assertEquals(LocalCutMouthWarpEffect.TYPE_ID, visualEffects[0].typeId)
        assertEquals(LocalCutEyeBlinkEffect.TYPE_ID, visualEffects[1].typeId)
        assertEquals("voice audio remains in the project", 1,
            project.tracks.first { it.type == TrackType.AUDIO }.clips.size)
        val output = File(context.cacheDir, "eyes-export-${System.nanoTime()}.mp4")
        var preview: Media3PreviewController? = null
        try {
            instrumentation.runOnMainSync {
                preview = Media3PreviewController(
                    context, ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
                ).also {
                    assertTrue("blink + TALK preview must load", it.load(project).isSuccess)
                    it.play()
                }
            }
            val state = runBlocking {
                withTimeout(20_000L) {
                    checkNotNull(preview).state.first {
                        it.errorMessage != null || it.positionUs >= 500_000L
                    }
                }
            }
            assertNull("eye blink + TALK preview cannot crash", state.errorMessage)
            export(project, output, instrumentation, context)
            assertTrue("blink + TALK MP4 must be created", output.isFile && output.length() > 0L)
            val eyesOpen = extractFrame(output, 80_000L)
            val eyesShut = extractFrame(output, 390_000L)
            try {
                assertEquals(eyesOpen.width, eyesShut.width)
                assertEquals(eyesOpen.height, eyesShut.height)
                val x = (eyesOpen.width * 0.4f).toInt()
                val y = (eyesOpen.height * 0.42f).toInt() + 6
                val delta = pixelRgbDelta(eyesOpen.getPixel(x, y),
                    eyesShut.getPixel(x, y))
                assertTrue("blink must visibly hide iris: RGB delta=$delta", delta >= 80)
                val backgroundDelta = pixelRgbDelta(
                    eyesOpen.getPixel(8, 8), eyesShut.getPixel(8, 8),
                )
                assertTrue("blink must not alter the background: RGB delta=$backgroundDelta",
                    backgroundDelta <= 25)
            } finally {
                eyesOpen.recycle()
                eyesShut.recycle()
            }
        } finally {
            preview?.let { p -> instrumentation.runOnMainSync { p.release() } }
            output.delete()
            image.delete()
            audio.delete()
        }
    }

    @Test
    fun bridgeFlatTalkReachesRealPreviewAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val imageFile = createFacePng(File(directory, "flat-mouth-${System.nanoTime()}.png"))
        val asset = imageAsset(context.packageName, imageFile)
        val seed = Project.create("flat-mouth-bridge-smoke", WIDTH, HEIGHT, 30)
        val visualTrack = seed.tracks.first { it.type == TrackType.VISUAL }.copy(
            clips = listOf(
                Clip(
                    id = BODY_CLIP_ID,
                    assetId = asset.id,
                    startUs = 0L,
                    durationUs = DURATION_US,
                    sourceInUs = 0L,
                    sourceOutUs = DURATION_US,
                ),
            ),
        )
        val project = seed.copy(
            id = PROJECT_ID,
            assets = listOf(asset),
            tracks = seed.tracks.map { track ->
                if (track.type == TrackType.VISUAL) visualTrack else track
            },
        )
        val session = projectSession(project)
        val executor = BridgeCommandExecutor(
            session = session,
            playback = object : BridgePlaybackPort {
                override val positionUs: Long = 0L
                override fun seekToUs(positionUs: Long): Result<Unit> = Result.success(Unit)
            },
        )

        val result = executor.execute(
            BridgeCommandEnvelope(
                protocolVersion = 1,
                messageId = "flat-mouth-smoke",
                kind = "command",
                deviceId = "emulator",
                projectId = PROJECT_ID,
                expectedRevision = INITIAL_REVISION,
                issuedAtEpochMs = 1_000L,
                expiresAtEpochMs = 31_000L,
                nonce = "flat-mouth-smoke-nonce",
                capability = BridgeCapability.ANIMATE_CHARACTER,
                payloadJson = """{
                    "action":"TALK",
                    "bodyClipId":"$BODY_CLIP_ID",
                    "startOffsetUs":0,
                    "durationUs":$DURATION_US,
                    "mouthRegion":{"centerX":0.5,"centerY":0.68,"radiusX":0.14,"radiusY":0.08},
                    "lipSyncCues":[
                        {"offsetUs":150000,"durationUs":250000,"openness":0.55},
                        {"offsetUs":550000,"durationUs":250000,"openness":1.0}
                    ]
                }""".trimIndent(),
            ),
        )

        assertTrue("Bridge TALK must succeed: ${result.code}", result.success)
        assertEquals(INITIAL_REVISION + 1L, result.projectRevision)
        assertTrue(
            "Bridge result must report flat-mouth effect use",
            result.resultJson.contains("\"flatMouthEffectApplied\":true"),
        )

        val mutated = session.state.project
        val body = checkNotNull(mutated.findClip(BODY_CLIP_ID))
        val mouthEffects = body.effects.filter { it.typeId == "localcut.character.mouth_warp" }
        assertEquals("exactly one mouth-warp effect must be owned by flat TALK", 1, mouthEffects.size)
        val openness = mouthEffects.single().animations.single { it.propertyId == "openness" }
        assertTrue("openness animation must have multiple time-varying keyframes", openness.keyframes.size >= 4)
        assertTrue("openness keyframes must stay inside clip", openness.keyframes.all { it.offsetUs in 0L..DURATION_US })

        var preview: Media3PreviewController? = null
        val output = File(context.cacheDir, "flat-mouth-export-${System.nanoTime()}.mp4")
        try {
            instrumentation.runOnMainSync {
                val created = Media3PreviewController(
                    context,
                    ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
                )
                preview = created
                val load = created.load(mutated)
                assertTrue("preview must load flat-mouth effect: ${load.exceptionOrNull()}", load.isSuccess)
                created.play()
            }

            val activePreview = checkNotNull(preview)
            val settled = runBlocking {
                withTimeout(20_000L) {
                    activePreview.state.first { state ->
                        state.errorMessage != null || state.positionUs >= 250_000L
                    }
                }
            }
            assertNull("preview must render without frame-processing error", settled.errorMessage)
            assertTrue("preview must advance through animated mouth effect", settled.positionUs >= 250_000L)

            export(mutated, output, instrumentation, context)
            assertTrue("exported MP4 must exist and be non-empty", output.isFile && output.length() > 0L)
            // Regression: a shader can compile, export, and report success while
            // rendering no movement. Require pixel changes near the character's
            // mouth during a known open cue, and an unchanged outside region.
            val closed = extractFrame(output, 50_000L)
            val speaking = extractFrame(output, 665_000L)
            try {
                assertEquals("frame width must be stable", closed.width, speaking.width)
                assertEquals("frame height must be stable", closed.height, speaking.height)
                val cx = speaking.width / 2
                val cy = (speaking.height * 0.68f).toInt()
                val mouthDelta = pixelRgbDelta(closed.getPixel(cx, cy), speaking.getPixel(cx, cy))
                assertTrue("mouth pixels must visibly change when TALK opens; delta=$mouthDelta",
                    mouthDelta >= 35)
                val outsideDelta = pixelRgbDelta(closed.getPixel(8, 8), speaking.getPixel(8, 8))
                assertTrue("unrelated pixels must stay stable; delta=$outsideDelta",
                    outsideDelta <= 25)
            } finally {
                closed.recycle()
                speaking.recycle()
            }
        } finally {
            preview?.let { controller -> instrumentation.runOnMainSync { controller.release() } }
            output.delete()
            imageFile.delete()
        }
    }


    @Test
    fun realLocalWavAnalysisAppliesTalkAndExportsMp4() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val imageFile = createFacePng(File(directory, "audio-mouth-face-" + System.nanoTime() + ".png"))
        val audioFile = createVoiceWav(File(directory, "audio-mouth-voice-" + System.nanoTime() + ".wav"))
        val faceAsset = imageAsset(context.packageName, imageFile)
        val voiceAsset = Asset(
            id = "voice-asset",
            type = AssetType.AUDIO,
            uri = FileProvider.getUriForFile(context, context.packageName + ".remote-media", audioFile).toString(),
            sourceDurationUs = DURATION_US,
            sizeBytes = audioFile.length(),
            displayName = audioFile.name,
            mimeType = "audio/wav",
            fingerprint = "wav-" + audioFile.length(),
        )
        val seed = Project.create("audio-driven-talk-smoke", WIDTH, HEIGHT, 30)
        val project = seed.copy(
            id = PROJECT_ID,
            assets = listOf(faceAsset, voiceAsset),
            tracks = seed.tracks.map { track ->
                when (track.type) {
                    TrackType.VISUAL -> track.copy(clips = listOf(
                        Clip(BODY_CLIP_ID, faceAsset.id, 0L, DURATION_US, 0L, DURATION_US),
                    ))
                    TrackType.AUDIO -> track.copy(clips = listOf(
                        Clip("voice-clip", voiceAsset.id, 0L, DURATION_US, 0L, DURATION_US),
                    ))
                }
            },
        )
        val session = projectSession(project)
        session.dispatch(EditorAction.SelectClip(BODY_CLIP_ID))
        val before = session.state
        val slice = AudioDrivenTalkPlanner.prepare(before.project, BODY_CLIP_ID, "voice-clip")
        val sourceUri = AssetUriResolver(context.contentResolver).resolve(voiceAsset).getOrThrow().toString()
        val decodeExecutor = Executors.newSingleThreadExecutor()
        val planningExecutor = Executors.newSingleThreadExecutor()
        var preview: Media3PreviewController? = null
        val output = File(context.cacheDir, "audio-driven-export-" + System.nanoTime() + ".mp4")
        try {
            val coordinator = WaveformLipSyncCueCoordinator(
                WaveformRepository(WaveformCache(4096), AndroidWaveformDecoder(context.contentResolver), decodeExecutor),
                planningExecutor,
            )
            val key = WaveformCacheKey(
                slice.audioFingerprint, slice.audioSourceInUs, slice.audioSourceOutUs, slice.bucketCount,
            )
            val latch = CountDownLatch(1)
            val received = AtomicReference<Result<List<LipSyncCue>>?>(null)
            coordinator.request(key, sourceUri, slice.durationUs) { decoded ->
                received.set(decoded)
                latch.countDown()
            }
            assertTrue("real background audio analysis must finish", latch.await(25, TimeUnit.SECONDS))
            val cues = requireNotNull(received.get()).getOrThrow()
            assertTrue("synthesized speech pulses must produce lip cues", cues.isNotEmpty())
            assertTrue("cues must remain bounded", cues.all {
                it.offsetUs >= 0L && it.durationUs > 0L && it.offsetUs + it.durationUs <= slice.durationUs
            })

            AudioDrivenTalkPlanner.requireUnchanged(before, INITIAL_REVISION, PROJECT_ID, slice)
            val planned = AudioDrivenTalkPlanner.plan(
                project, slice, MouthRegion(0.5, 0.68, 0.14, 0.08), cues,
            )
            session.dispatch(EditorAction.Execute(ApplyAnimationPlanCommand(planned)))
            assertEquals("single atomic edit", INITIAL_REVISION + 1L, session.state.revision)
            val edited = session.state.project
            val mouth = checkNotNull(edited.findClip(BODY_CLIP_ID)).effects.single {
                it.typeId == "localcut.character.mouth_warp"
            }
            assertTrue("mouth openness must use actual waveform cues",
                mouth.animations.single().keyframes.size >= 4)

            instrumentation.runOnMainSync {
                val controller = Media3PreviewController(
                    context, ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
                )
                preview = controller
                assertTrue("audio-derived TALK preview must load", controller.load(edited).isSuccess)
                controller.play()
            }
            val activePreview = checkNotNull(preview)
            val settled = runBlocking {
                withTimeout(20_000L) {
                    activePreview.state.first { it.errorMessage != null || it.positionUs >= 250_000L }
                }
            }
            assertNull("preview must have no video processing error", settled.errorMessage)
            export(edited, output, instrumentation, context)
            assertTrue("real audio-driven TALK export must produce MP4", output.isFile && output.length() > 0L)
        } finally {
            preview?.let { controller -> instrumentation.runOnMainSync { controller.release() } }
            decodeExecutor.shutdownNow()
            planningExecutor.shutdownNow()
            output.delete()
            imageFile.delete()
            audioFile.delete()
        }
    }

    @Test
    fun mouthPickerLoadsLocalPortraitMediaWithBoundedBitmapSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val file = File(directory, "mouth-visual-picker-" + System.nanoTime() + ".png")
        val original = Bitmap.createBitmap(900, 1600, Bitmap.Config.ARGB_8888)
        try {
            original.eraseColor(Color.rgb(200, 180, 160))
            file.outputStream().use { stream ->
                assertTrue("test image should be encoded", original.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            original.recycle()
        }
        try {
            val bitmap = loadPreviewBitmap(context, imageAsset(context.packageName, file))
            try {
                assertTrue("preview decode should be bounded", maxOf(bitmap.width, bitmap.height) <= 1024)
                assertTrue("decoded portrait geometry should be preserved", bitmap.height > bitmap.width)
                val fit = MouthRegionPlacementPolicy.fit(bitmap.width, bitmap.height, 400.0, 240.0)
                val sourceRegion = checkNotNull(
                    MouthRegionPlacementPolicy.place(
                        MouthRegion(0.5, 0.68, 0.14, 0.08),
                        200.0,
                        fit.topPx + fit.heightPx * 0.68,
                        fit,
                    ),
                )
                val effected = MouthRegionRenderProjectionPolicy.fromSourceToEffect(
                    sourceRegion,
                    bitmap.width,
                    bitmap.height,
                    1080,
                    1920,
                )
                assertTrue("projected mouth must remain within the rendered frame",
                    effected.centerX in effected.radiusX..(1.0 - effected.radiusX) &&
                        effected.centerY in effected.radiusY..(1.0 - effected.radiusY))
            } finally {
                bitmap.recycle()
            }
        } finally {
            file.delete()
        }
    }

    private fun extractFrame(file: File, timeUs: Long): Bitmap {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("Unable to decode TALK frame at $timeUs")
        } finally {
            retriever.release()
        }
    }

    private fun pixelRgbDelta(a: Int, b: Int): Int =
        kotlin.math.abs(Color.red(a) - Color.red(b)) +
            kotlin.math.abs(Color.green(a) - Color.green(b)) +
            kotlin.math.abs(Color.blue(a) - Color.blue(b))

    private fun projectSession(project: Project): ProjectSession = ProjectSession(
        initialProject = project,
        preview = object : ProjectPreviewLoader {
            override fun load(project: Project): Result<Unit> = Result.success(Unit)
        },
        autosave = DebouncedProjectSaver(
            store = object : ProjectStore {
                override fun save(project: Project): Result<Unit> = Result.success(Unit)
                override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
            },
            scheduler = object : DelayScheduler {
                override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable { }
            },
            delayMs = 300L,
        ),
        revisionClock = InMemoryProjectRevisionClock(mapOf(project.id to INITIAL_REVISION)),
    )

    private fun export(
        project: Project,
        outputFile: File,
        instrumentation: Instrumentation,
        context: Context,
    ) {
        val composition = ProjectCompositionCompiler(AssetUriResolver(context.contentResolver))
            .compile(project, RenderMode.EXPORT)
            .getOrThrow()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        instrumentation.runOnMainSync {
            val transformerBuilder = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        completed.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        failure.set(exportException)
                        completed.countDown()
                    }
                })
            LocalCutFrameProcessorIntegration.configureTransformerBuilder(
                builder = transformerBuilder,
                context = context,
                project = project,
            ).build().start(composition, outputFile.absolutePath)
        }
        assertTrue("Transformer export timed out", completed.await(120, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Transformer export failed", it) }
    }

    private fun imageAsset(packageName: String, file: File): Asset = Asset(
        id = "face-asset",
        type = AssetType.IMAGE,
        uri = FileProvider.getUriForFile(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "$packageName.remote-media",
            file,
        ).toString(),
        sourceDurationUs = null,
        sizeBytes = file.length(),
        displayName = file.name,
        mimeType = "image/png",
        fingerprint = "face-${file.length()}",
    )


    private fun createVoiceWav(file: File): File {
        val sampleRate = 44_100
        val samples = (DURATION_US * sampleRate / 1_000_000L).toInt()
        val audioBytes = samples * 2
        val bytes = ByteBuffer.allocate(44 + audioBytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII))
        bytes.putInt(36 + audioBytes)
        bytes.put("WAVE".toByteArray(Charsets.US_ASCII))
        bytes.put("fmt ".toByteArray(Charsets.US_ASCII))
        bytes.putInt(16)
        bytes.putShort(1.toShort())
        bytes.putShort(1.toShort())
        bytes.putInt(sampleRate)
        bytes.putInt(sampleRate * 2)
        bytes.putShort(2.toShort())
        bytes.putShort(16.toShort())
        bytes.put("data".toByteArray(Charsets.US_ASCII))
        bytes.putInt(audioBytes)
        repeat(samples) { sample ->
            val time = sample.toDouble() / sampleRate
            val amplitude: Short = when {
                time in 0.12..0.31 -> 14_000
                time in 0.54..0.78 -> 19_000
                else -> 0
            }.toShort()
            bytes.putShort(amplitude)
        }
        file.writeBytes(bytes.array())
        return file
    }

    private fun createFacePng(file: File): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(235, 220, 200))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        canvas.drawCircle(WIDTH * 0.40f, HEIGHT * 0.42f, 8f, paint)
        canvas.drawCircle(WIDTH * 0.60f, HEIGHT * 0.42f, 8f, paint)
        paint.strokeWidth = 6f
        canvas.drawLine(WIDTH * 0.42f, HEIGHT * 0.68f, WIDTH * 0.58f, HEIGHT * 0.68f, paint)
        file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        bitmap.recycle()
        return file
    }

    private companion object {
        const val PROJECT_ID = "flat-mouth-project"
        const val BODY_CLIP_ID = "body"
        const val INITIAL_REVISION = 7L
        const val WIDTH = 320
        const val HEIGHT = 180
        const val DURATION_US = 1_000_000L
    }
}
