package com.localcut.editor.mobile

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

/** A local declarative recipe, never executable code or external URLs. */
internal object AutomaticVideoRecipe {
    const val MAX_CHARS = 65_536
    const val EXAMPLE = """{"version":1,"format":"9:16","scenes":[{"image":1,"durationMs":5000}]}"""

    fun parse(text: String, imageCount: Int): AutomaticVideoPlan {
        require(text.length in 1..MAX_CHARS) { "Roteiro vazio ou grande demais" }
        return JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            var version: Long? = null
            var format: String? = null
            var scenes: List<AutomaticScene>? = null
            reader.beginObject()
            val fields = mutableSetOf<String>()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(fields.add(name)) { "Campo duplicado: $name" }
                when (name) {
                    "version" -> version = integer(reader)
                    "format" -> {
                        require(reader.peek() == JsonToken.STRING) { "Formato deve ser texto" }
                        format = reader.nextString()
                    }
                    "scenes" -> {
                        reader.beginArray()
                        val values = mutableListOf<AutomaticScene>()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            val seen = mutableSetOf<String>()
                            var image: Int? = null
                            var duration: Long? = null
                            while (reader.hasNext()) {
                                val key = reader.nextName()
                                require(seen.add(key)) { "Campo repetido na cena" }
                                when (key) {
                                    "image" -> {
                                        val number = integer(reader)
                                        require(number in 1L..Int.MAX_VALUE.toLong()) { "Índice inválido" }
                                        image = number.toInt()
                                    }
                                    "durationMs" -> duration = integer(reader)
                                    else -> error("Campo de cena não suportado: $key")
                                }
                            }
                            reader.endObject()
                            values += AutomaticScene(requireNotNull(image) { "Cena sem image" }, requireNotNull(duration) { "Cena sem durationMs" })
                        }
                        reader.endArray()
                        scenes = values
                    }
                    else -> error("Campo não suportado: $name")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Conteúdo extra após o roteiro" }
            require(version == 1L) { "Versão de roteiro não suportada" }
            AutomaticVideoPlan.build(requireNotNull(format), requireNotNull(scenes), imageCount)
        }
    }

    private fun integer(reader: JsonReader): Long {
        require(reader.peek() == JsonToken.NUMBER) { "Esperado número inteiro" }
        return reader.nextString().toLongOrNull() ?: error("Esperado número inteiro")
    }
}
