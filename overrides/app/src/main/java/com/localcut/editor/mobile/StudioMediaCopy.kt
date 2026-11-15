package com.localcut.editor.mobile

import java.io.InputStream
import java.io.OutputStream

/** Bounded local copy; caller owns streams and partial-file cleanup. */
internal fun copyStudioMedia(
    input: InputStream,
    output: OutputStream,
    checkCancelled: () -> Unit = {},
    availableBytes: () -> Long,
    limit: Long = 128L * 1024 * 1024,
    reserve: Long = 16L * 1024 * 1024,
): Long {
    require(limit > 0 && reserve >= 0 && reserve <= Long.MAX_VALUE - 65536)
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        checkCancelled()
        val count = input.read(buffer)
        if (count < 0) break
        require(count > 0 && total <= limit - count) { "Mídia excede limite técnico de importação" }
        require(availableBytes() >= reserve + count) { "Espaço insuficiente" }
        output.write(buffer, 0, count)
        total += count
    }
    checkCancelled()
    require(total > 0) { "Mídia vazia" }
    return total
}
