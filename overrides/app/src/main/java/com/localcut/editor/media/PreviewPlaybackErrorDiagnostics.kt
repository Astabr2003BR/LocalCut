package com.localcut.editor.media

/** Show only numeric error codes and exception types, never private paths or tokens. */
internal object PreviewPlaybackErrorDiagnostics {
    fun describe(errorCode: Int, error: Throwable): String {
        val causes = mutableListOf<String>()
        val visited = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>(),
        )
        var current: Throwable? = error
        while (current != null && causes.size < 5 && visited.add(current)) {
            val typeName = current.javaClass.simpleName
            causes.add(if (typeName.matches(Regex("[A-Za-z0-9_$]{1,80}"))) typeName else "Exception")
            current = current.cause
        }
        return "Prévia Media3: código $errorCode | ${causes.joinToString(" > ")}"
    }
}
