package com.localcut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolbarLayoutPolicyTest {
    @Test
    fun compactLayoutIsUsedOnNarrowPhones() {
        assertTrue(EditorToolbarLayoutPolicy.useCompactLayout(widthDp = 360f))
        assertTrue(EditorToolbarLayoutPolicy.useCompactLayout(widthDp = 411f))
    }

    @Test
    fun singleRowLayoutIsKeptWhenThereIsEnoughWidth() {
        assertFalse(EditorToolbarLayoutPolicy.useCompactLayout(widthDp = 600f))
    }
}
