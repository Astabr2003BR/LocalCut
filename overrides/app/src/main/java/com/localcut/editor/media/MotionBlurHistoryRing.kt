package com.localcut.editor.media

internal class MotionBlurHistoryRing(
    private val capacity: Int,
) {
    init {
        require(capacity >= 0) { "motion blur history capacity must be non-negative" }
    }

    private var count = 0
    private var next = 0

    val size: Int get() = count

    fun nextWriteIndex(): Int? = if (capacity == 0) null else next

    fun commitWrite() {
        if (capacity == 0) return
        next = (next + 1) % capacity
        count = minOf(count + 1, capacity)
    }

    fun newestFirstIndices(): List<Int> =
        List(count) { age -> Math.floorMod(next - 1 - age, capacity) }

    fun reset() {
        count = 0
        next = 0
    }
}
