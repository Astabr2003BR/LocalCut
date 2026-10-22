package com.localcut.editor.media

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class LegacyUnavailablePreviewSmokeTest {
    @Test
    fun unavailableLegacyImageDoesNotLeavePreviewAtZeroDuration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val durationUs = 5_000_000L
        val missing = Asset(
            id = "legacy-image",
            type = AssetType.IMAGE,
            uri = Uri.parse("content://${context.packageName}.missing/${System.nanoTime()}.jpg").toString(),
            sourceDurationUs = null,
            sizeBytes = 100L,
            displayName = "218803.jpg",
            mimeType = "image/jpeg",
            fingerprint = "legacy-missing",
        )
        val base = Project.create("legacy-preview", 1080, 1920, 30)
        val project = base.copy(
            assets = listOf(missing),
            tracks = base.tracks.map { track ->
                if (track.type == TrackType.VISUAL) {
                    track.copy(
                        clips = listOf(
                            Clip(
                                id = "legacy-clip",
                                assetId = missing.id,
                                startUs = 0L,
                                durationUs = durationUs,
                                sourceInUs = 0L,
                                sourceOutUs = durationUs,
                            ),
                        ),
                    )
                } else {
                    track
                }
            },
        )

        lateinit var controller: Media3PreviewController
        val loadResult = AtomicReference<Result<Unit>>()
        instrumentation.runOnMainSync {
            controller = Media3PreviewController(
                context,
                ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
            )
            loadResult.set(controller.load(project))
        }

        try {
            assertTrue(
                "legacy unavailable media must not make preview.load fail: ${loadResult.get().exceptionOrNull()}",
                loadResult.get().isSuccess,
            )
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250)
            assertEquals(
                "timeline duration must remain visible even when a legacy asset is unavailable",
                durationUs,
                controller.state.value.durationUs,
            )
            assertNull(
                "unavailable legacy media must not poison the entire preview player",
                controller.state.value.errorMessage,
            )
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }
}
