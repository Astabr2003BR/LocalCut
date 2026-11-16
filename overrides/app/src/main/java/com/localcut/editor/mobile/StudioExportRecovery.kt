package com.localcut.editor.mobile

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Private, bounded record of studio preparation/export attempts.
 * Does not store a destination URI, recipe, authorization or media. Only a new process
 * may recover interrupted work; a renderer in this process may still own its files.
 */
internal class StudioExportRecovery(
    context: Context,
    private val owner: String = PROCESS_OWNER,
    preferencesName: String = "localcut_studio_export_recovery_v1",
) {
    private val prefs = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val cacheRoot = context.applicationContext.cacheDir.canonicalFile

    data class Recovery(val interrupted: Int, val cleanupPending: Int)

    fun begin(id: String): Boolean = synchronized(LOCK) {
        require(validId(id)) { "Invalid studio export id" }
        val key = PREFIX + id
        // An existing directory must never be adopted as a new job.
        if (prefs.contains(key) || File(cacheRoot, "automatic-$id").exists() || prefs.all.size >= MAX_RECORDS) return@synchronized false
        prefs.edit().putString(key, owner).commit()
    }

    fun finish(id: String): Boolean = synchronized(LOCK) {
        require(validId(id)) { "Invalid studio export id" }
        val key = PREFIX + id
        if (prefs.all[key] != owner) return@synchronized false
        removeWork(id) && prefs.edit().remove(key).commit()
    }

    fun recover(): Recovery = synchronized(LOCK) {
        var interrupted = 0
        var pending = 0
        for ((key, value) in prefs.all) {
            if (!key.startsWith(PREFIX)) continue
            val id = key.removePrefix(PREFIX)
            // Never interpret arbitrary stored paths as cleanup targets.
            if (!validId(id) || value !is String || !validId(value)) { pending++; continue }
            if (value == owner) continue
            interrupted++
            if (!removeWork(id) || !prefs.edit().remove(key).commit()) pending++
        }
        Recovery(interrupted, pending)
    }

    private fun removeWork(id: String): Boolean = runCatching {
        val dir = File(cacheRoot, "automatic-$id")
        // Only the flat private directory created by AutomaticVideoProjectBuilder.
        // Reject aliases and nested directories instead of recursively following them.
        if (dir.canonicalFile != dir.absoluteFile) return@runCatching false
        if (!dir.exists()) return@runCatching true
        if (!dir.isDirectory) return@runCatching false
        val children = dir.listFiles() ?: return@runCatching false
        if (children.any { it.isDirectory || it.canonicalFile != it.absoluteFile }) return@runCatching false
        for (child in children) if (!child.delete()) return@runCatching false
        dir.delete()
    }.getOrDefault(false)

    companion object {
        private const val PREFIX = "job:"
        private const val MAX_RECORDS = 32
        private val PROCESS_OWNER = UUID.randomUUID().toString()
        private val LOCK = Any()
        private fun validId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    }
}
