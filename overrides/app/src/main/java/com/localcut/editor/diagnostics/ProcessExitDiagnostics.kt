package com.localcut.editor.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object ProcessExitDiagnostics {
    private const val PREFS_NAME = "localcut.process.diagnostics.v1"
    private const val KEY_LAST_CRASH = "last_uncaught_crash"
    private const val KEY_LAST_CRASH_AT = "last_uncaught_crash_at"
    private const val MAX_STATE_SUMMARY_BYTES = 120
    private const val MAX_CRASH_CHARS = 1800
    private const val MAX_REPORT_CHARS = 2200
    private const val MAX_BREADCRUMBS = 4

    private val installed = AtomicBoolean(false)
    private val breadcrumbs = ArrayDeque<String>()

    @Volatile
    private var pendingReport: String? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        pendingReport = latestRelevantExitReport(appContext)
        installCrashHandler(appContext)
        mark(appContext, "app:create")
    }

    fun consumePendingReport(): String? {
        val report = pendingReport
        pendingReport = null
        return report
    }

    fun mark(context: Context, event: String) {
        val safeEvent = event
            .replace(Regex("[^A-Za-z0-9:_=.-]"), "_")
            .take(72)

        val summary = synchronized(breadcrumbs) {
            if (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.removeFirst()
            breadcrumbs.addLast(safeEvent)
            breadcrumbs.joinToString(">")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val manager = context.applicationContext.getSystemService(ActivityManager::class.java)
                val bytes = summary.toByteArray(Charsets.US_ASCII)
                manager.setProcessStateSummary(
                    if (bytes.size <= MAX_STATE_SUMMARY_BYTES) bytes else bytes.copyOf(MAX_STATE_SUMMARY_BYTES),
                )
            }
        }
    }

    fun latestRelevantExitReport(context: Context): String? {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedCrash = preferences
            .getString(KEY_LAST_CRASH, null)
            ?.takeIf { it.isNotBlank() }
        val storedCrashAt = preferences.getLong(KEY_LAST_CRASH_AT, 0L)

        val exit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                appContext
                    .getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(appContext.packageName, 0, 12)
                    .firstOrNull { ProcessExitDiagnosticsPolicy.shouldSurface(it.reason) }
            }.getOrNull()
        } else {
            null
        }

        val crash = when {
            storedCrash == null -> null
            exit == null -> storedCrash
            ProcessExitDiagnosticsPolicy.shouldAttachStoredCrash(
                exitReason = exit.reason,
                exitTimestampMs = exit.timestamp,
                crashTimestampMs = storedCrashAt,
            ) -> storedCrash
            else -> null
        }

        if (exit == null && crash == null) return null

        val batteryOptimized = runCatching {
            val power = appContext.getSystemService(PowerManager::class.java)
            !power.isIgnoringBatteryOptimizations(appContext.packageName)
        }.getOrDefault(true)

        return buildString {
            append("DIAGNÓSTICO DE ENCERRAMENTO\n")
            if (exit != null) {
                append(formatExit(exit))
            } else {
                append("Android não retornou motivo histórico anormal.\n")
            }
            append("Otimização de bateria: ")
            append(if (batteryOptimized) "ATIVA" else "SEM RESTRIÇÃO")
            if (crash != null) {
                append("\n\nÚltima exceção não tratada deste encerramento:\n")
                append(crash.take(900))
            }
        }.take(MAX_REPORT_CHARS)
    }

    private fun formatExit(exit: ApplicationExitInfo): String = buildString {
        val description = exit.description?.trim().orEmpty()

        append("Motivo: ")
        append(ProcessExitDiagnosticsPolicy.labelFor(exit.reason, description))
        append(" (reason=")
        append(exit.reason)
        append(")\n")
        append("Status: ")
        append(exit.status)
        append(" | Importância: ")
        append(exit.importance)
        append("\n")
        append("Quando: ")
        append(SimpleDateFormat("dd/MM HH:mm:ss", Locale.ROOT).format(Date(exit.timestamp)))
        append("\n")
        append("Memória: PSS=")
        append(exit.pss / 1024L)
        append("MB RSS=")
        append(exit.rss / 1024L)
        append("MB\n")

        if (description.isNotBlank()) {
            append("Descrição: ")
            append(description.take(420))
            append("\n")
        }

        ProcessExitDiagnosticsPolicy.interpretationFor(exit.reason, description)?.let { interpretation ->
            append("Interpretação: ")
            append(interpretation)
            append("\n")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val state = runCatching { exit.processStateSummary }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.toString(Charsets.US_ASCII)
                ?.trim()
            if (!state.isNullOrBlank()) {
                append("Estado antes de morrer: ")
                append(state.take(300))
                append("\n")
            }
        }
    }

    private fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val crashText = buildString {
                    append(thread.name.take(80))
                    append(": ")
                    append(error.stackTraceToString())
                }
                    .replace(Regex("(?i)Bearer\\s+\\S+"), "Bearer [redacted]")
                    .replace(Regex("(?:content|file)://\\S+"), "[redacted-uri]")
                    .take(MAX_CRASH_CHARS)

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, crashText)
                    .putLong(KEY_LAST_CRASH_AT, System.currentTimeMillis())
                    .commit()
                mark(context, "uncaught:${error.javaClass.simpleName}")
            }

            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
