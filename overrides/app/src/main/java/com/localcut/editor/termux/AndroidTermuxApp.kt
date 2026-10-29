package com.localcut.editor.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

object AndroidTermuxApp {
    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(TermuxEngineRecoveryPolicy.TERMUX_PACKAGE) != null

    fun launch(context: Context): Boolean {
        val intent = context.packageManager
            .getLaunchIntentForPackage(TermuxEngineRecoveryPolicy.TERMUX_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun copyCommand(context: Context, label: String, command: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, command))
    }
}
