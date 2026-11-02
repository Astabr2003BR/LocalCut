package com.localcut.editor.bridge.transport

import kotlin.math.pow
import kotlin.math.roundToLong

class BridgeReconnectPolicy(
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 60_000L,
    private val jitterFraction: Double = 0.20,
) {
    init {
        require(baseDelayMs > 0L) { "baseDelayMs must be positive" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
        require(jitterFraction in 0.0..1.0) { "jitterFraction must be in 0..1" }
    }

    fun delayMs(attempt: Int, randomUnit: Double): Long {
        require(attempt >= 0) { "attempt must be non-negative" }
        require(randomUnit in 0.0..1.0) { "randomUnit must be in 0..1" }

        val exponent = attempt.coerceAtMost(62)
        val exponential = baseDelayMs.toDouble() * 2.0.pow(exponent.toDouble())
        val cappedBase = exponential.coerceAtMost(maxDelayMs.toDouble())
        val centered = (randomUnit * 2.0) - 1.0
        val jittered = cappedBase * (1.0 + centered * jitterFraction)
        return jittered.roundToLong().coerceIn(0L, maxDelayMs)
    }

    fun shouldReconnect(manuallyDisconnected: Boolean, sessionEnabled: Boolean): Boolean =
        sessionEnabled && !manuallyDisconnected
}
