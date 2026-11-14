package com.localcut.editor.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TikTokCarouselPolicyTest {
    @Test fun preservesOrderDeduplicatesAndCaps() {
        val incoming = (1..40).map { "page-$it" }
        val result = TikTokCarouselPolicy.add(listOf("page-2"), incoming)
        assertEquals(35, result.size)
        assertEquals(listOf("page-2", "page-1", "page-3"), result.take(3))
        assertEquals(35, result.distinct().size)
    }

    @Test fun movesWithoutDroppingPages() {
        val pages = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), TikTokCarouselPolicy.move(pages, 0, 2))
        assertEquals(pages, TikTokCarouselPolicy.move(pages, -1, 1))
        assertEquals(pages, TikTokCarouselPolicy.move(pages, 1, 1))
    }

    @Test fun fitsWithoutCropping() {
        assertEquals(1080 to 1350, TikTokCarouselPolicy.fittedSize(1080, 1350, 1080, 1920))
        assertEquals(1080 to 607, TikTokCarouselPolicy.fittedSize(1920, 1080, 1080, 1350))
        assertEquals(1 to 1350, TikTokCarouselPolicy.fittedSize(1, 100000, 1080, 1350))
        assertThrows(IllegalArgumentException::class.java) { TikTokCarouselPolicy.fittedSize(0, 1, 1080, 1350) }
    }

    @Test fun boundsVideoDuration() {
        assertEquals(5_000_000L, TikTokCarouselPolicy.durationUs(5))
        assertThrows(IllegalArgumentException::class.java) { TikTokCarouselPolicy.durationUs(0) }
        assertThrows(IllegalArgumentException::class.java) { TikTokCarouselPolicy.durationUs(16) }
    }
}
