package com.localcut.editor.domain.design

enum class OnionSkinColorMode {
    ORIGINAL,
    TRADITIONAL,
}

object OnionSkinColorPolicy {
    const val PREVIOUS_ARGB: Int = 0xFFFF4D5A.toInt()
    const val NEXT_ARGB: Int = 0xFF4DFF88.toInt()

    fun argbFor(
        relativeFrame: Int,
        originalArgb: Int,
        mode: OnionSkinColorMode,
    ): Int {
        require(relativeFrame != 0) { "current frame is not an onion-skin ghost" }
        return when (mode) {
            OnionSkinColorMode.ORIGINAL -> originalArgb
            OnionSkinColorMode.TRADITIONAL -> if (relativeFrame < 0) PREVIOUS_ARGB else NEXT_ARGB
        }
    }
}
