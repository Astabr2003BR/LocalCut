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
        } finally {
            preview?.let { controller -> instrumentation.runOnMainSync { controller.release() } }
            output.delete()
            imageFile.delete()
        }
    }

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
