package com.localcut.editor.domain.animation

import java.util.Locale

/**
 * PT-BR spelling-to-mouth-shape approximation, NOT speech recognition.
 * A user-provided transcript supplies the sequence; audio still controls
 * openness through the existing TALK waveform track.
 */
object PortugueseVisemeGuide {
    private const val MAX_TEXT_LENGTH = 160
    private const val MAX_EVENTS = 96
    const val MIN_DURATION_US = 400_000L
    const val MAX_DURATION_US = 120_000_000L

    fun plan(text: String, durationUs: Long): List<VisemeCue> {
        require(text.length in 1..MAX_TEXT_LENGTH) { "Texto deve ter de 1 a 160 caracteres" }
        require(durationUs in MIN_DURATION_US..MAX_DURATION_US) {
            "Duração de fala fora do intervalo permitido"
        }
        val symbols = mutableListOf<Pair<VisemeShape, Int>>()
        fun append(shape: VisemeShape, weight: Int) {
            if (symbols.isNotEmpty() && symbols.last().first == shape) {
                val prior = symbols.last()
                symbols[symbols.lastIndex] = shape to (prior.second + weight)
            } else {
                symbols += shape to weight
            }
        }
        for (ch in text.lowercase(Locale.ROOT)) {
            when (ch) {
                'a', 'á', 'à', 'â', 'ã' -> append(VisemeShape.A, 3)
                'e', 'é', 'ê', 'i', 'í', 'y' -> append(VisemeShape.E_I, 3)
                'o', 'ó', 'ô', 'õ' -> append(VisemeShape.O, 3)
                'u', 'ú', 'ü' -> append(VisemeShape.U, 3)
                'm', 'b', 'p' -> append(VisemeShape.M_B_P, 2)
                'f', 'v' -> append(VisemeShape.F_V, 2)
                'l', 'r' -> append(VisemeShape.L, 2)
                's', 't', 'd', 'z', 'c', 'ç', 'j', 'x', 'g', 'q', 'n', 'h', 'k', 'w' ->
                    append(VisemeShape.S_T, 2)
                ' ', '\n', '\t', '\r', '-', '_' -> append(VisemeShape.REST, 1)
                ',', ';', ':' -> append(VisemeShape.REST, 3)
                '.', '!', '?', '…' -> append(VisemeShape.REST, 5)
                else -> Unit // unsupported symbols do not execute or introduce effects
            }
        }
        require(symbols.any { it.first != VisemeShape.REST }) {
            "O texto precisa conter letras da fala"
        }
        require(symbols.size <= MAX_EVENTS) {
            "Fala muito longa. Divida em trechos menores."
        }
        val total = symbols.sumOf { it.second }.toLong()
        var progress = 0L
        val result = ArrayList<VisemeCue>(symbols.size)
        for ((shape, weight) in symbols) {
            val begin = durationUs * progress / total
            progress += weight
            val end = durationUs * progress / total
            if (end > begin) result += VisemeCue(begin, end - begin, shape)
        }
        return result
    }
}
