package com.localcut.editor.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokPostDraftTest {
    @Test fun emptyDraftDoesNotProduceClipboardText() {
        assertEquals("", TikTokPostDraft().fullText())
    }

    @Test fun formatsTitleDescriptionAndHashtags() {
        val draft = TikTokPostDraft(" Capítulo 2 ", " Leia agora ", "manga #anime")
        assertEquals("Leia agora\n\n#manga #anime", draft.caption())
        assertEquals("Capítulo 2\n\nLeia agora\n\n#manga #anime", draft.fullText())
    }

    @Test fun normalizesDeduplicatesAndCapsTags() {
        val draft = TikTokPostDraft(hashtags = "#Manga manga, anime; #suspense #webtoon #BR #extra #bad-tag")
        assertEquals("#Manga #anime #suspense #webtoon #BR", draft.normalizedHashtags())
    }

    @Test fun supportsUnicodeAndBoundsInputs() {
        val draft = TikTokPostDraft("a".repeat(100), "b".repeat(2100), "#mangá " + "x".repeat(310)).bounded()
        assertEquals(90, draft.title.length)
        assertEquals(2000, draft.description.length)
        assertEquals(300, draft.hashtags.length)
        assertTrue(draft.normalizedHashtags().startsWith("#mangá"))
    }

    @Test fun presetContainsDisclosureAndFiveTags() {
        val draft = TikTokPostDraft.mangaSuspense()
        assertTrue(draft.description.contains("IA"))
        assertEquals(5, draft.normalizedHashtags().split(" ").size)
    }
}
