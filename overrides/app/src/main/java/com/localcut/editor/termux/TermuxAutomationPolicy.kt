package com.localcut.editor.termux

object TermuxAutomationPolicy {
    const val MIN_WAKE_INTERVAL_MS: Long = 60_000L
    const val RECOVERY_RETRY_DELAY_MS: Long = 1_500L
    const val MANAGED_AUTOMATION_CAPABILITY = "MANAGED_AUTOMATION_V1"

    val REQUIRED_CREATIVE_CAPABILITIES: Set<String> = setOf(
        "FFMPEG",
        "FFPROBE",
        "REVERSE_MEDIA",
        "DOWNLOAD_GENERATED_MEDIA",
        "ANALYZE_TRACKING",
        "ANALYZE_OBJECT_TRACKING",
    )

    fun missingCreativeCapabilities(capabilities: Set<String>): Set<String> =
        REQUIRED_CREATIVE_CAPABILITIES - capabilities

    fun hasFullCreativeCapabilities(capabilities: Set<String>): Boolean =
        missingCreativeCapabilities(capabilities).isEmpty()

    fun hasManagedAutomationCapability(capabilities: Set<String>): Boolean =
        MANAGED_AUTOMATION_CAPABILITY in capabilities

    fun needsMaintenance(
        paired: Boolean,
        managedReady: Boolean,
        connected: Boolean,
        capabilities: Set<String>,
    ): Boolean {
        if (!paired || !managedReady) return false
        if (!connected) return true
        return !hasFullCreativeCapabilities(capabilities)
    }

    fun shouldAttemptWake(lastAttemptMs: Long, nowMs: Long): Boolean =
        lastAttemptMs <= 0L || nowMs - lastAttemptMs >= MIN_WAKE_INTERVAL_MS
}
