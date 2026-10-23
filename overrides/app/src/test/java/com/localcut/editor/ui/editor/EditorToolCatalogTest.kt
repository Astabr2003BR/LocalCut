package com.localcut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolCatalogTest {
    @Test
    fun projectSelectionKeepsCoreCreationToolsReachable() {
        val ids = EditorToolCatalog.primaryToolsFor(EditorSelectionKind.PROJECT).map { it.id }.toSet()

        assertTrue(EditorToolId.MEDIA in ids)
        assertTrue(EditorToolId.AUDIO in ids)
        assertTrue(EditorToolId.TEXT in ids)
        assertTrue(EditorToolId.OVERLAY in ids)
        assertTrue(EditorToolId.DESIGN in ids)
        assertTrue(EditorToolId.CANVAS in ids)
    }

    @Test
    fun creationToolsRemainReachableWhileVideoClipIsSelected() {
        val ids = EditorToolCatalog.primaryToolsFor(EditorSelectionKind.VIDEO).map { it.id }.toSet()

        assertTrue(EditorToolId.MEDIA in ids)
        assertTrue(EditorToolId.AUDIO in ids)
        assertTrue(EditorToolId.TEXT in ids)
        assertTrue(EditorToolId.OVERLAY in ids)
    }

    @Test
    fun videoSelectionExposesProfessionalClipTools() {
        val ids = EditorToolCatalog.primaryToolsFor(EditorSelectionKind.VIDEO).map { it.id }.toSet()

        assertTrue(EditorToolId.EDIT in ids)
        assertTrue(EditorToolId.EFFECTS in ids)
        assertTrue(EditorToolId.COLOR in ids)
        assertTrue(EditorToolId.SPEED in ids)
        assertTrue(EditorToolId.MASK in ids)
        assertTrue(EditorToolId.CHROMA in ids)
        assertTrue(EditorToolId.ANIMATION in ids)
        assertTrue(EditorToolId.LAYERS in ids)
    }

    @Test
    fun layerPanelIsLiveOnlyAfterRealVisibilityBackendExists() {
        val layers = EditorToolCatalog.all.single { it.id == EditorToolId.LAYERS }

        assertTrue(layers.enabled)
        assertTrue(layers.state == EditorToolState.LIVE)
    }

    @Test
    fun audioSelectionDoesNotOfferVisualOnlyTools() {
        val ids = EditorToolCatalog.primaryToolsFor(EditorSelectionKind.AUDIO).map { it.id }.toSet()

        assertTrue(EditorToolId.AUDIO in ids)
        assertFalse(EditorToolId.MASK in ids)
        assertFalse(EditorToolId.CHROMA in ids)
        assertFalse(EditorToolId.COLOR in ids)
    }

    @Test
    fun generativeAiIsNotPartOfTheActiveCatalog() {
        assertTrue(EditorToolCatalog.all.none { tool ->
            tool.id.name.contains("AI", ignoreCase = true) ||
                tool.label.contains("IA", ignoreCase = true) ||
                tool.label.contains("AI", ignoreCase = true)
        })
    }

    @Test
    fun plannedToolsAreNeverEnabled() {
        assertTrue(EditorToolCatalog.all.filter { it.state == EditorToolState.PLANNED }.all { !it.enabled })
    }
}
