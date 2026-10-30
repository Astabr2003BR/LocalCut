package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3CompositorSettingsPolicyTest {
    @Test
    fun customSettingsAreRequiredForAlphaOrBlendButNotPlainSingleLayer() {
        assertFalse(Media3CompositorSettingsPolicy.requiresCustomSettings(listOf(sequence(BlendMode.NORMAL))))
        assertTrue(Media3CompositorSettingsPolicy.requiresCustomSettings(listOf(sequence(BlendMode.MULTIPLY))))
        assertTrue(
            Media3CompositorSettingsPolicy.requiresCustomSettings(
                listOf(sequence(BlendMode.NORMAL, collisionPolicy = TrackCollisionPolicy.OVERLAY)),
            ),
        )
    }

    private fun sequence(
        mode: BlendMode,
        collisionPolicy: TrackCollisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
    ): Media3SequenceBlueprint = Media3SequenceBlueprint(
        type = TrackType.VISUAL,
        sourceCollisionPolicy = collisionPolicy,
        items = listOf(
            Media3BlueprintItem.Media(
                clipId = "clip-$mode",
                assetId = "asset-$mode",
                assetType = AssetType.VIDEO,
                uri = "content://$mode",
                outputTracks = Media3OutputTracks.VIDEO_ONLY,
                sourceMediaDurationUs = 1_000L,
                presentationDurationUs = 1_000L,
                sourceInUs = 0L,
                sourceOutUs = 1_000L,
                transform = TransformValue(),
                opacity = 1.0,
                volume = 1.0,
                keyframes = emptyList(),
                imageFrameRate = null,
                compositing = CompositingSettings(blendMode = mode),
            ),
        ),
    )
}
