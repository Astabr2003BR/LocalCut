package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendModeUiPolicyTest {
    @Test
    fun optionsExposeEverySupportedBlendModeExactlyOnce() {
        val options = BlendModeUiPolicy.options()

        assertEquals(BlendMode.entries.toSet(), options.map { it.mode }.toSet())
        assertEquals(options.size, options.map { it.mode }.distinct().size)
        assertTrue(options.all { it.label.isNotBlank() })
    }

    @Test
    fun normalModeIsFirstAndLabelsAreMobileFriendly() {
        val options = BlendModeUiPolicy.options()

        assertEquals(BlendMode.NORMAL, options.first().mode)
        assertEquals("Normal", options.first().label)
        assertEquals("Multiplicar", options.first { it.mode == BlendMode.MULTIPLY }.label)
        assertEquals("Tela", options.first { it.mode == BlendMode.SCREEN }.label)
        assertEquals("Sobrepor", options.first { it.mode == BlendMode.OVERLAY }.label)
        assertEquals("Escurecer", options.first { it.mode == BlendMode.DARKEN }.label)
        assertEquals("Clarear", options.first { it.mode == BlendMode.LIGHTEN }.label)
        assertEquals("Adicionar", options.first { it.mode == BlendMode.ADD }.label)
        assertEquals("Subtrair", options.first { it.mode == BlendMode.SUBTRACT }.label)
    }
}
