package com.localcut.editor.diagnostics

import android.app.ApplicationExitInfo
import java.util.Locale
import kotlin.math.abs

object ProcessExitDiagnosticsPolicy {
    private const val CRASH_ASSOCIATION_WINDOW_MS = 2 * 60_000L
    private const val SWIPE_UP_CLEAN = "swipeupclean"

    fun shouldSurface(reason: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_OTHER -> true
        else -> false
    }

    fun shouldAttachStoredCrash(
        exitReason: Int,
        exitTimestampMs: Long,
        crashTimestampMs: Long,
    ): Boolean {
        if (exitReason != ApplicationExitInfo.REASON_CRASH) return false
        if (exitTimestampMs <= 0L || crashTimestampMs <= 0L) return false
        return abs(exitTimestampMs - crashTimestampMs) <= CRASH_ASSOCIATION_WINDOW_MS
    }

    fun labelFor(reason: Int, description: String? = null): String = when {
        isRecentAppsCleanup(reason, description) -> "LIMPEZA DOS RECENTES"
        else -> labelForReason(reason)
    }

    fun interpretationFor(reason: Int, description: String?): String? = when {
        isRecentAppsCleanup(reason, description) ->
            "Encerramento solicitado pelo launcher/OEM ao limpar o app dos recentes; não classificado como pressão de memória."
        else -> null
    }

    private fun isRecentAppsCleanup(reason: Int, description: String?): Boolean =
        reason == ApplicationExitInfo.REASON_OTHER && normalizeDescription(description) == SWIPE_UP_CLEAN

    private fun normalizeDescription(description: String?): String = description
        .orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s_-]+"), "")

    private fun labelForReason(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH_JAVA"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "UNKNOWN_$reason"
    }
}
