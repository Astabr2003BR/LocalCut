package com.localcut.editor.media

import java.util.ArrayDeque

internal class BlendOutputLeasePolicy(
    private val capacity: Int,
) {
    private val inUse = ArrayDeque<Long>()

    init {
        require(capacity >= 1) { "BLEND_OUTPUT_CAPACITY_INVALID" }
    }

    fun canAcquire(): Boolean = inUse.size < capacity

    fun isEmpty(): Boolean = inUse.isEmpty()

    fun onAcquired(presentationTimeUs: Long) {
        require(presentationTimeUs >= 0L) { "BLEND_OUTPUT_TIME_INVALID" }
        require(canAcquire()) { "BLEND_OUTPUT_CAPACITY_EXHAUSTED" }
        val previous = inUse.peekLast()
        require(previous == null || presentationTimeUs > previous) {
            "BLEND_OUTPUT_TIMESTAMPS_NOT_STRICTLY_INCREASING"
        }
        inUse.addLast(presentationTimeUs)
    }

    fun releaseThrough(presentationTimeUs: Long): List<Long> {
        require(presentationTimeUs >= 0L) { "BLEND_OUTPUT_RELEASE_TIME_INVALID" }
        val released = mutableListOf<Long>()
        while (inUse.isNotEmpty() && inUse.peekFirst() <= presentationTimeUs) {
            released += inUse.removeFirst()
        }
        return released
    }
}
