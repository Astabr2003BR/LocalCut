package com.localcut.editor.mobile

internal data class AutomaticScene(val image: Int, val durationMs: Long, val sourceInMs: Long = 0L)
internal data class AutomaticTimedScene(val imageIndex: Int, val startUs: Long, val durationUs: Long, val sourceInUs: Long = 0L) {
    val sourceOutUs: Long get() = sourceInUs + durationUs
}
internal data class AutomaticVideoPlan(val width: Int, val height: Int, val scenes: List<AutomaticTimedScene>, val durationUs: Long) {
    companion object {
        fun build(format: String, scenes: List<AutomaticScene>, imageCount: Int): AutomaticVideoPlan {
            val (width, height) = when (format) {
                "9:16" -> 1080 to 1920
                "4:5" -> 1080 to 1350
                "16:9" -> 1920 to 1080
                "1:1" -> 1080 to 1080
                else -> error("Formato inválido")
            }
            require(imageCount > 0 && scenes.isNotEmpty()) { "Selecione imagens e informe cenas" }
            var position = 0L
            val timed = scenes.map { scene ->
                require(scene.image in 1..imageCount) { "Imagem ${scene.image} não selecionada" }
                require(scene.durationMs in 34L..Long.MAX_VALUE / 1000L) { "Duração inválida (mínimo: 34 ms)" }
                val duration = scene.durationMs * 1000L
                require(scene.sourceInMs in 0L..Long.MAX_VALUE / 1000L) { "Início do trecho inválido" }
                val sourceIn = scene.sourceInMs * 1000L
                require(sourceIn <= Long.MAX_VALUE - duration) { "Fim do trecho excede a representação suportada" }
                require(position <= Long.MAX_VALUE - duration) { "Duração total excede a representação suportada" }
                AutomaticTimedScene(scene.image - 1, position, duration, sourceIn).also { position += duration }
            }
            return AutomaticVideoPlan(width, height, timed, position)
        }
    }
}
