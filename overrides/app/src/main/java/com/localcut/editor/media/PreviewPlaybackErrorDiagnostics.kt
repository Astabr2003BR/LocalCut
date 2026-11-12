package com.localcut.editor.media

/** Show only numeric error codes and exception types, never private paths or tokens. */
internal object PreviewPlaybackErrorDiagnostics {
    fun describe(errorCode: Int, error: Throwable): String {
        val causes = mutableListOf<String>()
        val visited = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>(),
        )
        var current: Throwable? = error
        var root: Throwable? = null
        while (current != null && causes.size < 5 && visited.add(current)) {
            val typeName = current.javaClass.simpleName
            causes.add(if (typeName.matches(Regex("[A-Za-z0-9_$]{1,80}"))) typeName else "Exception")
            root = current
            current = current.cause
        }
        // Show only a trusted class and method from the innermost failure.
        // Never include exception messages, paths, URIs, accounts or tokens.
        val origin = root?.stackTrace?.firstOrNull { frame ->
            val type = frame.className
            val method = frame.methodName
            type != "androidx.media3.common.util.Assertions" &&
                type.matches(Regex("(?:androidx\\.media3|com\\.localcut\\.editor)\\.[A-Za-z0-9_$.]{1,140}")) &&
                method.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]{0,80}"))
        }?.let { frame -> " | origem: ${frame.className}.${frame.methodName}" }.orEmpty()
        return ("Prévia Media3: código $errorCode | ${causes.joinToString(" > ")}$origin").take(300)
    }
}
