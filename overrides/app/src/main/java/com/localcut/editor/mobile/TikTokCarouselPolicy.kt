package com.localcut.editor.mobile

enum class CarouselFormat(val width: Int, val height: Int) {
    MANGA(1080, 1350), VERTICAL(1080, 1920)
}

object TikTokCarouselPolicy {
    const val MAX_PAGES = 35
    const val MIN_SECONDS = 2
    const val MAX_SECONDS = 15

    fun add(existing: List<String>, incoming: List<String>): List<String> =
        (existing + incoming).filter { it.isNotBlank() }.distinct().take(MAX_PAGES)

    fun move(pages: List<String>, from: Int, to: Int): List<String> {
        if (from !in pages.indices || to !in pages.indices || from == to) return pages
        return pages.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun fittedSize(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Pair<Int, Int> {
        require(sourceWidth > 0 && sourceHeight > 0 && width > 0 && height > 0)
        val scale = minOf(width.toDouble() / sourceWidth, height.toDouble() / sourceHeight)
        return (sourceWidth * scale).toInt().coerceIn(1, width) to
            (sourceHeight * scale).toInt().coerceIn(1, height)
    }

    fun durationUs(seconds: Int): Long {
        require(seconds in MIN_SECONDS..MAX_SECONDS)
        return seconds * 1_000_000L
    }
}
