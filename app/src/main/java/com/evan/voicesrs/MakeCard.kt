package com.evan.voicesrs

/**
 * Draft a short Basic Q&A from an explanation. The app never creates note types.
 */
object MakeCard {
    const val DEFAULT_DECK = "Databricks DEA::Generated"
    const val TAG = "ai-generated"
    const val MODEL = "Basic"

    fun system(): String =
        "You draft a short Anki Basic card for the Databricks Certified Data Engineer Associate exam. " +
            "Reply with exactly two lines and nothing else: a line starting \"Q: \" then a line starting \"A: \". " +
            "The question is one sentence. The answer is one or two sentences. No markdown."

    fun userMessage(front: String, back: String, explanation: String): String {
        return buildString {
            append("Card front: ").append(AnkiText.stripHtml(front)).append('\n')
            append("Card back: ").append(AnkiText.stripHtml(back))
            val expl = explanation.trim()
            if (expl.isNotEmpty()) {
                append("\nExplanation: ").append(expl)
            }
        }
    }

    fun parse(text: String): Pair<String, String>? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines().map { it.trim() }
            .filter { it.isNotEmpty() }
        var q = ""
        var a = ""
        for (line in lines) {
            when {
                line.startsWith("Q:", ignoreCase = true) -> q = line.substringAfter(':').trim()
                line.startsWith("A:", ignoreCase = true) -> a = line.substringAfter(':').trim()
                line.startsWith("Front:", ignoreCase = true) -> q = line.substringAfter(':').trim()
                line.startsWith("Back:", ignoreCase = true) -> a = line.substringAfter(':').trim()
            }
        }
        if (q.isBlank() || a.isBlank()) return null
        return q to a
    }
}
