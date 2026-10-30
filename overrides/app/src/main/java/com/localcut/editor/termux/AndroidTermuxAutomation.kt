package com.localcut.editor.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AndroidTermuxAutomation {
    private const val PREFS = "localcut_termux_automation"
    private const val LAST_WAKE_MS = "last_wake_ms"
    private const val MANAGED_READY = "managed_ready"

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission(TermuxRunCommandSpec.PERMISSION) == PackageManager.PERMISSION_GRANTED


    fun isManagedReady(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(MANAGED_READY, false)

    fun markManagedReady(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MANAGED_READY, true)
            .apply()
    }

    fun wake(
        context: Context,
        force: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): Result<Boolean> = runCatching {
        val appContext = context.applicationContext
        check(AndroidTermuxApp.isInstalled(appContext)) { "TERMUX_APP_NOT_INSTALLED" }
        check(hasRunCommandPermission(appContext)) { "TERMUX_RUN_COMMAND_PERMISSION_REQUIRED" }

        val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastWakeMs = preferences.getLong(LAST_WAKE_MS, 0L)
        if (!force && !TermuxAutomationPolicy.shouldAttemptWake(lastWakeMs, nowMs)) {
            return@runCatching false
        }

        val intent = Intent(TermuxRunCommandSpec.ACTION).apply {
            component = ComponentName(TermuxEngineRecoveryPolicy.TERMUX_PACKAGE, TermuxRunCommandSpec.SERVICE_CLASS)
            putExtra(TermuxRunCommandSpec.EXTRA_PATH, TermuxRunCommandSpec.BASH_PATH)
            putExtra(TermuxRunCommandSpec.EXTRA_ARGUMENTS, TermuxRunCommandSpec.arguments())
            putExtra(TermuxRunCommandSpec.EXTRA_WORKDIR, TermuxRunCommandSpec.HOME_PATH)
            putExtra(TermuxRunCommandSpec.EXTRA_BACKGROUND, true)
        }
        checkNotNull(appContext.startService(intent)) { "TERMUX_RUN_COMMAND_DISPATCH_FAILED" }
        preferences.edit().putLong(LAST_WAKE_MS, nowMs).apply()
        true
    }
}
