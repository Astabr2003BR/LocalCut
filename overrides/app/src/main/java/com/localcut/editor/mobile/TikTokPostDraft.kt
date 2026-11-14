package com.localcut.editor.mobile

/** Local editorial draft; no network calls, account access or automatic posting. */
internal data class TikTokPostDraft(
    val title: String = "",
    val description: String = "",
    val hashtags: String = "",
) {
    fun bounded() = copy(
        title = title.take(90),
        description = description.take(2000),
        hashtags = hashtags.take(300),
    )

    fun normalizedHashtags(): String = hashtags
        .split(Regex("[\\s,;]+"))
        .map { it.trim().trimStart('#') }
        .filter { it.isNotBlank() && it.all { ch -> ch.isLetterOrDigit() || ch == '_' } }
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }
        .take(5)
        .joinToString(" ") { "#$it" }

    fun caption(): String = listOf(description.trim(), normalizedHashtags())
        .filter { it.isNotBlank() }.joinToString("\n\n")

    fun fullText(): String = listOf(title.trim(), caption())
        .filter { it.isNotBlank() }.joinToString("\n\n")

    companion object {
        fun mangaSuspense() = TikTokPostDraft(
            title = "A Mensagem que Chegou às 3H",
            description = "Às 3 da manhã, uma mensagem de um número desconhecido mudou tudo. O que você faria no lugar de Lucas? Deslize para ler e conte sua teoria nos comentários. Mangá original de suspense, com arte criada por IA.",
            hashtags = "#manga #anime #mangabrasil #suspense #AMensagemDas3H",
        )
    }
}
