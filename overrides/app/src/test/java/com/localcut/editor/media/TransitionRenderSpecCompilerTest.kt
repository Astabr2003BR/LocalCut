package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionRenderSpecCompilerTest {
    @Test
    fun fadeToBlackCreatesOutgoingAndIncomingEnvelopesWithoutChangingDuration() {
        val project = sequentialProject(
            transitions = listOf(transition(TransitionKind.FADE_TO_BLACK, 500_000L)),
        )

        val specs = TransitionRenderSpecCompiler.compile(project).getOrThrow()

        assertEquals(2, specs.size)
        assertEquals(
            ClipTransitionRenderSpec(
                clipId = "a",
                fadeInUs = 0L,
                fadeOutUs = 500_000L,
            ),
            specs.getValue("a"),
        )
        assertEquals(
            ClipTransitionRenderSpec(
                clipId = "b",
                fadeInUs = 500_000L,
                fadeOutUs = 0L,
            ),
            specs.getValue("b"),
        )
        assertEquals(6_000_000L, project.tracks.first().clips.sumOf { it.durationUs })
    }

    @Test
    fun middleClipCanHaveFadeInAndFadeOutFromNeighborTransitions() {
        val project = threeClipProject(
            transitions = listOf(
                ClipTransition("t-ab", "a", "b", 400_000L, TransitionKind.FADE_TO_BLACK),
                ClipTransition("t-bc", "b", "c", 700_000L, TransitionKind.FADE_TO_BLACK),
            ),
        )

        val specs = TransitionRenderSpecCompiler.compile(project).getOrThrow()

        assertEquals(400_000L, specs.getValue("b").fadeInUs)
        assertEquals(700_000L, specs.getValue("b").fadeOutUs)
    }

    @Test
    fun dissolveAndWipeFailExplicitlyInsteadOfRenderingWithoutEffect() {
        listOf(TransitionKind.DISSOLVE, TransitionKind.WIPE_LEFT).forEach { kind ->
            val result = TransitionRenderSpecCompiler.compile(
                sequentialProject(transitions = listOf(transition(kind, 500_000L))),
            )

            assertTrue("$kind must fail until its compositor exists", result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains(kind.name))
        }
    }

    @Test
    fun noTransitionsProducesNoPerClipEffects() {
        assertTrue(TransitionRenderSpecCompiler.compile(sequentialProject()).getOrThrow().isEmpty())
    }

    private fun transition(kind: TransitionKind, durationUs: Long) = ClipTransition(
        id = "t-ab",
        fromClipId = "a",
        toClipId = "b",
        durationUs = durationUs,
        kind = kind,
    )

    private fun sequentialProject(transitions: List<ClipTransition> = emptyList()): Project {
        val asset = videoAsset()
        return Project.create("Transitions", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip("a", asset.id, 0L, 3_000_000L, 0L, 3_000_000L),
                        Clip("b", asset.id, 3_000_000L, 3_000_000L, 3_000_000L, 6_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            transitions = transitions,
        )
    }

    private fun threeClipProject(transitions: List<ClipTransition>): Project {
        val asset = videoAsset()
        return Project.create("Transitions", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip("a", asset.id, 0L, 3_000_000L, 0L, 3_000_000L),
                        Clip("b", asset.id, 3_000_000L, 3_000_000L, 3_000_000L, 6_000_000L),
                        Clip("c", asset.id, 6_000_000L, 3_000_000L, 6_000_000L, 9_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            transitions = transitions,
        )
    }

    private fun videoAsset() = Asset(
        id = "video",
        type = AssetType.VIDEO,
        uri = "content://video",
        sourceDurationUs = 12_000_000L,
        sizeBytes = 1L,
        displayName = "video.mp4",
        mimeType = "video/mp4",
        fingerprint = "video",
    )
}
