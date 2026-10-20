package com.localcut.editor.media

import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3VisualLayerOrderTest {
    @Test
    fun media3InputOrderPlacesHigherLocalCutTrackFirst() {
        val base = layer(id = "base", order = 0, policy = TrackCollisionPolicy.EXCLUSIVE)
        val overlay = layer(id = "overlay", order = 1, policy = TrackCollisionPolicy.OVERLAY)

        val ordered = Media3VisualLayerOrder.toInputOrder(listOf(base, overlay))

        assertEquals(listOf("overlay", "base"), ordered.map { it.sourceTrackId })
    }

    private fun layer(
        id: String,
        order: Int,
        policy: TrackCollisionPolicy,
    ) = Media3SequenceBlueprint(
        type = TrackType.VISUAL,
        items = emptyList(),
        sourceTrackId = id,
        sourceTrackOrder = order,
        sourceCollisionPolicy = policy,
        laneIndex = 0,
    )
}
