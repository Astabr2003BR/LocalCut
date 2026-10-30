package com.localcut.editor

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import com.localcut.editor.bridge.agent.BridgeColdStartRecovery
import com.localcut.editor.diagnostics.ProcessExitDiagnostics
import com.localcut.editor.termux.AndroidTermuxAutomation
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocalCutApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var coldStartRecoveryAttempted = false
    private var generatedMediaCleanupAttempted = false

    override fun onCreate() {
        super.onCreate()
        ProcessExitDiagnostics.install(this)
        registerActivityLifecycleCallbacks(this)
        runCatching {
            val termuxRuntime = TermuxAgentEngineRuntime.get(this)
            if (
                termuxRuntime.tokenStore.load() != null &&
                AndroidTermuxAutomation.isManagedReady(this)
            ) {
                termuxRuntime.wakeAutomation()
                    .onSuccess { dispatched ->
                        ProcessExitDiagnostics.mark(this, "termux:auto-wake:dispatched=$dispatched")
                    }
                    .onFailure { error ->
                        ProcessExitDiagnostics.mark(this, "termux:auto-wake:failed=${error.message}")
                    }
            }
        }.onFailure { error ->
            ProcessExitDiagnostics.mark(this, "termux:auto-wake:init-failed=${error.message}")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        ProcessExitDiagnostics.mark(activity, "activity:create:${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        ProcessExitDiagnostics.mark(activity, "activity:start:${activity.javaClass.simpleName}")
        if (!coldStartRecoveryAttempted) {
            coldStartRecoveryAttempted = true
            val reconnected = BridgeColdStartRecovery(this).reconnectIfNeeded()
            ProcessExitDiagnostics.mark(this, "bridge:cold-start-reconnect=$reconnected")
        }
        if (!generatedMediaCleanupAttempted) {
            generatedMediaCleanupAttempted = true
            val runtime = LocalCutProcessRuntime.get(this)
            val startupState = runtime.session.state
            runtime.processScope.launch(Dispatchers.IO) {
                TermuxAgentEngineRuntime.get(this@LocalCutApplication).collectGeneratedMediaOrphans(
                    project = startupState.project,
                    canUndo = startupState.canUndo,
                    canRedo = startupState.canRedo,
                )
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        ProcessExitDiagnostics.mark(activity, "activity:resume:${activity.javaClass.simpleName}")
        val report = ProcessExitDiagnostics.consumePendingReport() ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        AlertDialog.Builder(activity)
            .setTitle("Diagnóstico LocalCut")
            .setMessage(report)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copiar") { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("LocalCut diagnóstico", report))
                Toast.makeText(activity, "Diagnóstico copiado", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onActivityPaused(activity: Activity) {
        ProcessExitDiagnostics.mark(activity, "activity:pause:${activity.javaClass.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        ProcessExitDiagnostics.mark(activity, "activity:stop:${activity.javaClass.simpleName}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        ProcessExitDiagnostics.mark(
            activity,
            "activity:destroy:${activity.javaClass.simpleName}:finish=${activity.isFinishing}",
        )
    }
}
