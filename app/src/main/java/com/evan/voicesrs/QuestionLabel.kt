package com.evan.voicesrs

/**
 * One label for a card. A number printed on the card ("Question 4", "Q4",
 * "Q.4", a leading "4." or "4)") becomes QUESTION 4 and is removed from the
 * stem that is shown and spoken. Otherwise the session count is CARD N.
 * [ContentHash] keeps hashing the original field text.
 */
object QuestionLabel {
    data class Face(
        val label: String,
        val stem: String,
        val spoken: String,
        val fromCard: Boolean
    )

    private val questionWord = Regex("""(?i)^question\s+(\d+)\b""")
    private val qDotted = Regex("""(?i)^q\.\s*(\d+)\b""")
    private val qTight = Regex("""(?i)^q(\d+)\b""")
    private val leading = Regex("""^(\d+)[.)](?!\d)""")

    /** Display and spoken face from HTML or already-plain card text. */
    fun of(source: String, sessionCount: Int): Face {
        val plain = BubbleModel.fullQuestion(source).ifBlank { AnkiText.stripHtml(source) }
        return resolve(plain, sessionCount.coerceAtLeast(1))
    }

    /**
     * Label for prompts. A number printed on the card always wins.
     * [sessionCount] 0 omits CARD N so a lookahead stays cache-stable.
     */
    fun promptName(source: String, sessionCount: Int): String {
        val face = of(source, sessionCount.coerceAtLeast(1))
        if (face.fromCard) return face.label
        if (sessionCount > 0) return face.label
        return ""
    }

    fun resolve(plain: String, sessionCount: Int): Face {
        val text = plain.trim()
        val count = sessionCount.coerceAtLeast(1)
        val hit = match(text)
        if (hit == null) return Face("CARD $count", text, text, fromCard = false)
        val stem = stripPrefix(text, hit.end)
        return Face("QUESTION ${hit.number}", stem, stem, fromCard = true)
    }

    private data class Hit(val number: Int, val end: Int)

    private fun match(text: String): Hit? {
        questionWord.find(text)?.let { found ->
            if (found.range.first == 0 && !decimalAfter(text, found.range.last + 1)) {
                return Hit(found.groupValues[1].toInt(), found.range.last + 1)
            }
        }
        qDotted.find(text)?.let { found ->
            if (found.range.first == 0 && !decimalAfter(text, found.range.last + 1)) {
                return Hit(found.groupValues[1].toInt(), found.range.last + 1)
            }
        }
        qTight.find(text)?.let { found ->
            if (found.range.first == 0 && !decimalAfter(text, found.range.last + 1)) {
                return Hit(found.groupValues[1].toInt(), found.range.last + 1)
            }
        }
        leading.find(text)?.let { found ->
            if (found.range.first == 0) {
                return Hit(found.groupValues[1].toInt(), found.range.last + 1)
            }
        }
        return null
    }

    /** "4.5" and "Q4.5" are sizes and versions, not a question number. */
    private fun decimalAfter(text: String, index: Int): Boolean {
        if (index >= text.length || text[index] != '.') return false
        val next = text.getOrNull(index + 1) ?: return false
        return next.isDigit()
    }

    private fun stripPrefix(text: String, end: Int): String {
        var i = end
        if (i < text.length && text[i] == ':') i++
        else if (i < text.length && text[i] == '.') {
            val next = text.getOrNull(i + 1)
            if (next == null || next.isWhitespace()) i++
        }
        return text.substring(i).trim()
    }
}