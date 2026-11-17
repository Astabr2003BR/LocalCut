package com.localcut.editor.domain.animation

import java.math.BigDecimal

/** Bounded data-only reader for Rhubarb's timestamp/shape TSV output.
 * No executable code, paths, network, audio decoding or project mutation. */
object RhubarbCueImport {
    const val MAX_CUES = 96
    const val MAX_TEXT_LENGTH = 8192
    private val timePattern = Regex("[0-9]{1,3}(\\.[0-9]{1,6})?")

    fun parse(text: String, clipDurationUs: Long): List<VisemeCue> {
        require(clipDurationUs in 1L..120_000_000L) { "Duração inválida" }
        require(text.length in 1..MAX_TEXT_LENGTH) { "TSV vazio ou muito grande" }
        val rows = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(rows.size in 2..(MAX_CUES + 1)) { "Quantidade de registros inválida" }
        val entries = rows.map { row ->
            val columns = row.split('\t')
            require(columns.size == 2) { "Use dois campos separados por tabulação" }
            val time = columns[0].trim()
            require(timePattern.matches(time)) { "Tempo inválido" }
            val us = BigDecimal(time).movePointRight(6).longValueExact()
            require(us in 0L..clipDurationUs) { "Tempo fora do clipe" }
            val shape = when (columns[1].trim()) {
                "A" -> VisemeShape.M_B_P
                "B" -> VisemeShape.S_T
                "C" -> VisemeShape.E_I
                "D" -> VisemeShape.A
                "E" -> VisemeShape.O
                "F" -> VisemeShape.U
                "G" -> VisemeShape.F_V
                "H" -> VisemeShape.L
                "X" -> VisemeShape.REST
                else -> throw IllegalArgumentException("Código de boca inválido")
            }
            us to shape
        }
        require(entries.first().first == 0L) { "O primeiro tempo deve ser zero" }
        require(entries.zipWithNext().all { (a, b) -> a.first < b.first }) {
            "Tempos precisam ser crescentes e únicos"
        }
        require(entries.last().second in setOf(VisemeShape.REST, VisemeShape.M_B_P)) {
            "O último registro precisa indicar boca fechada"
        }
        return entries.zipWithNext().map { (a, b) ->
            VisemeCue(a.first, b.first - a.first, a.second)
        }
    }
}
