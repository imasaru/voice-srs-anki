package com.evan.voicesrs

/**
 * Pre-reveal replies must not name the correct option. [guard] retries once,
 * then redacts remaining leaks.
 */
object AnswerLeak {
    const val PLACEHOLDER = "[withheld]"
    const val REWRITE =
        "Your last reply named the correct option. Rewrite without naming the correct option, its letter, or its keyword."

    private val stop = setOf(
        "with", "from", "that", "this", "have", "been", "they", "them", "then",
        "than", "also", "into", "over", "under", "option", "answer", "choice",
        "none", "both", "all", "the", "and", "for", "are", "but", "not"
    )

    data class Secrets(
        val letter: String?,
        val phrases: List<String>
    ) {
        val empty: Boolean get() = letter.isNullOrBlank() && phrases.isEmpty()
    }

    fun secrets(front: String, correctField: String): Secrets {
        val field = correctField.trim()
        if (field.isEmpty()) return Secrets(null, emptyList())
        val stem = QuestionLabel.of(front, 1).stem.ifBlank {
            BubbleModel.fullQuestion(front).ifBlank { front }
        }
        val parsed = QuestionCard.parse(stem)
        val letter = QuestionCard.correctLetter(
            QuestionCard.highlightSource(field, ""),
            parsed.options
        )
        val opt = parsed.options.firstOrNull { it.letter.equals(letter, ignoreCase = true) }
        val phrases = ArrayList<String>()
        val text = AnkiText.stripHtml(opt?.text ?: field).trim()
        if (text.length >= 3 && !text.matches(Regex("[A-F][.)]?", RegexOption.IGNORE_CASE))) {
            phrases += text
            for (token in tokenize(text)) {
                if (token.length >= 4 && token.lowercase() !in stop &&
                    phrases.none { it.equals(token, ignoreCase = true) }
                ) {
                    phrases += token
                }
            }
        }
        return Secrets(letter, phrases)
    }

    fun leaked(reply: String, secrets: Secrets): Boolean {
        if (secrets.empty || reply.isBlank()) return false
        for (phrase in secrets.phrases) {
            if (containsPhrase(reply, phrase)) return true
        }
        val letter = secrets.letter ?: return false
        val named = Regex(
            """(?i)\b(?:answer|option|choice|letter|correct)\b[^\n.]{0,40}\b$letter\b"""
        ).containsMatchIn(reply)
        val claimed = Regex(
            """(?i)\b$letter\b\s+(?:is|is the)\s+(?:correct|right|answer)"""
        ).containsMatchIn(reply)
        return named || claimed
    }

    fun redact(reply: String, secrets: Secrets): String {
        if (secrets.empty) return reply
        var out = reply
        val phrases = secrets.phrases.sortedByDescending { it.length }
        for (phrase in phrases) {
            out = Regex("""(?i)${Regex.escape(phrase)}""").replace(out, PLACEHOLDER)
        }
        val letter = secrets.letter
        if (!letter.isNullOrBlank()) {
            out = Regex(
                """(?i)\b(?:answer|option|choice|letter)\s*(?:is|:)?\s*$letter\b"""
            ).replace(out, PLACEHOLDER)
            out = Regex(
                """(?i)\b$letter\b\s+(?:is|is the)\s+(?:correct|right|answer)\b"""
            ).replace(out, PLACEHOLDER)
        }
        return out.replace(Regex("""(?:\s*$PLACEHOLDER){2,}"""), " $PLACEHOLDER")
    }

    fun guard(reply: String, secrets: Secrets, retry: () -> String?): String {
        if (!leaked(reply, secrets)) return reply
        val second = try {
            retry()
        } catch (_: Exception) {
            null
        }
        if (!second.isNullOrBlank() && !leaked(second, secrets)) return second
        return redact(second ?: reply, secrets)
    }

    private fun tokenize(text: String): List<String> {
        return Regex("[A-Za-z][A-Za-z0-9_]+").findAll(text).map { it.value }.toList()
    }

    private fun containsPhrase(reply: String, phrase: String): Boolean {
        if (phrase.isBlank()) return false
        if (phrase.any { it == '.' || it == '(' || it == '_' }) {
            return reply.contains(phrase, ignoreCase = true)
        }
        return Regex("""(?i)\b${Regex.escape(phrase)}\b""").containsMatchIn(reply)
    }
}
