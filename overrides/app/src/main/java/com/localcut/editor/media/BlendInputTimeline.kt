package com.localcut.editor.media

import java.util.ArrayDeque
import java.util.TreeMap
import kotlin.math.max

internal data class BlendTimelineFrame<T>(
    val presentationTimeUs: Long,
    val payload: T,
)

internal class BlendInputTimeline<T> {
    private data class Source<T>(
        val frames: ArrayDeque<BlendTimelineFrame<T>> = ArrayDeque(),
        var ended: Boolean = false,
    )

    private val sources = TreeMap<Int, Source<T>>()
    private var primaryInputId: Int? = null

    fun registerInput(inputId: Int) {
        require(inputId == sources.size) { "BLEND_INPUT_IDS_NOT_CONTIGUOUS" }
        require(!sources.containsKey(inputId)) { "BLEND_INPUT_ALREADY_REGISTERED" }
        sources[inputId] = Source()
        if (primaryInputId == null) primaryInputId = inputId
    }

    fun queue(
        inputId: Int,
        presentationTimeUs: Long,
        payload: T,
    ): List<BlendTimelineFrame<T>> {
        require(presentationTimeUs >= 0L) { "BLEND_INPUT_TIME_INVALID" }
        val source = sources[inputId] ?: throw IllegalArgumentException("BLEND_INPUT_NOT_REGISTERED")
        require(!source.ended) { "BLEND_INPUT_ALREADY_ENDED" }
        val previous = source.frames.peekLast()?.presentationTimeUs
        require(previous == null || presentationTimeUs > previous) {
            "BLEND_INPUT_TIMESTAMPS_NOT_STRICTLY_INCREASING"
        }
        source.frames.addLast(BlendTimelineFrame(presentationTimeUs, payload))
        return if (inputId == primaryInputId) {
            pruneAllSecondary()
        } else {
            pruneSecondary(source)
        }
    }

    fun endInput(inputId: Int) {
        val source = sources[inputId] ?: throw IllegalArgumentException("BLEND_INPUT_NOT_REGISTERED")
        require(!source.ended) { "BLEND_INPUT_ALREADY_ENDED" }
        source.ended = true
    }

    fun selectForNextOutput(): List<BlendTimelineFrame<T>>? {
        if (sources.size < 2 || sources.values.any { it.frames.isEmpty() }) return null
        val primaryId = primaryInputId ?: return null
        val primaryFrame = sources.getValue(primaryId).frames.peekFirst() ?: return null
        val selected = ArrayList<BlendTimelineFrame<T>>(sources.size)

        sources.forEach { (inputId, source) ->
            if (inputId == primaryId) {
                selected += primaryFrame
            } else {
                val selectedTime = BlendFrameSelectionPolicy.selectClosest(
                    primaryTimeUs = primaryFrame.presentationTimeUs,
                    secondaryTimesUs = source.frames.map { it.presentationTimeUs },
                    secondaryEnded = source.ended,
                ) ?: return null
                selected += source.frames.first { it.presentationTimeUs == selectedTime }
            }
        }
        return selected
    }

    fun consumePrimaryAndPrune(): List<BlendTimelineFrame<T>> {
        val primaryId = primaryInputId ?: return emptyList()
        val primary = sources.getValue(primaryId)
        val released = mutableListOf<BlendTimelineFrame<T>>()
        primary.pollFirst()?.let(released::add)
        released += pruneAllSecondary()
        return released
    }

    fun allEndedAndPrimaryEmpty(): Boolean {
        val primaryId = primaryInputId ?: return false
        return sources.isNotEmpty() &&
            sources.values.all { it.ended } &&
            sources.getValue(primaryId).frames.isEmpty()
    }

    fun drainAll(): List<BlendTimelineFrame<T>> = buildList {
        sources.values.forEach { source ->
            while (source.frames.isNotEmpty()) add(source.frames.removeFirst())
        }
    }

    private fun pruneAllSecondary(): List<BlendTimelineFrame<T>> {
        val primaryId = primaryInputId ?: return emptyList()
        return buildList {
            sources.forEach { (inputId, source) ->
                if (inputId != primaryId) addAll(pruneSecondary(source))
            }
        }
    }

    private fun pruneSecondary(source: Source<T>): List<BlendTimelineFrame<T>> {
        val primary = primaryInputId?.let(sources::get) ?: return emptyList()
        if (primary.frames.isEmpty() && primary.ended) {
            return buildList {
                while (source.frames.isNotEmpty()) add(source.frames.removeFirst())
            }
        }
        val nextPrimaryTimeUs = primary.frames.peekFirst()?.presentationTimeUs ?: return emptyList()
        val atOrBefore = source.frames.count { it.presentationTimeUs <= nextPrimaryTimeUs }
        val releaseCount = max(atOrBefore - 1, 0)
        return List(releaseCount) { source.frames.removeFirst() }
    }
}
