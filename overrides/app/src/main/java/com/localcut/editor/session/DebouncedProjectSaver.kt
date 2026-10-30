package com.localcut.editor.session

import com.localcut.editor.domain.model.Project

class DebouncedProjectSaver(
    private val store: ProjectStore,
    private val scheduler: DelayScheduler,
    private val delayMs: Long
) {
    init { require(delayMs >= 0) { "delayMs must be >= 0" } }

    private val stateLock = Any()
    private val saveLock = Any()
    private var pending: Project? = null
    private var scheduled: Cancellable? = null
    private var automaticRetryAvailable = false

    @Volatile
    var lastResult: Result<Unit>? = null
        private set

    fun submit(project: Project) {
        synchronized(stateLock) {
            pending = project
            automaticRetryAvailable = true
            if (scheduled == null) {
                scheduled = scheduler.schedule(delayMs) { flushPending() }
            }
        }
    }

    fun flushNow(): Result<Unit> {
        synchronized(stateLock) {
            scheduled?.cancel()
            scheduled = null
        }
        return flushPending()
    }

    fun cancelPending() {
        synchronized(stateLock) {
            scheduled?.cancel()
            scheduled = null
            pending = null
            automaticRetryAvailable = false
        }
    }

    private fun flushPending(): Result<Unit> {
        synchronized(stateLock) { scheduled = null }
        return synchronized(saveLock) {
            val project = synchronized(stateLock) { pending }
                ?: return@synchronized Result.success(Unit).also { lastResult = it }
            val result = store.save(project)
            synchronized(stateLock) {
                if (result.isSuccess && pending == project) {
                    pending = null
                    automaticRetryAvailable = false
                } else if (result.isFailure && pending == project && automaticRetryAvailable && scheduled == null) {
                    automaticRetryAvailable = false
                    scheduled = scheduler.schedule(delayMs) { flushPending() }
                }
                lastResult = result
            }
            result
        }
    }
}
