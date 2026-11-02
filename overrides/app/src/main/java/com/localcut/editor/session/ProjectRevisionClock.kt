package com.localcut.editor.session

import android.content.Context

interface ProjectRevisionClock {
    fun current(projectId: String): Long
    fun next(projectId: String): Long
}

class InMemoryProjectRevisionClock(
    initial: Map<String, Long> = emptyMap()
) : ProjectRevisionClock {
    private val revisions = initial.toMutableMap()

    override fun current(projectId: String): Long = revisions[projectId] ?: 0L

    override fun next(projectId: String): Long {
        val next = current(projectId) + 1L
        revisions[projectId] = next
        return next
    }
}

class SharedPreferencesProjectRevisionClock(context: Context) : ProjectRevisionClock {
    private val preferences = context.getSharedPreferences("localcut.project.revisions", Context.MODE_PRIVATE)

    override fun current(projectId: String): Long = preferences.getLong(key(projectId), 0L)

    override fun next(projectId: String): Long {
        val next = current(projectId) + 1L
        preferences.edit().putLong(key(projectId), next).commit()
        return next
    }

    private fun key(projectId: String): String = "revision.$projectId"
}
