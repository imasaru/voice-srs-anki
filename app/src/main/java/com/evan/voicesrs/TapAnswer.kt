package com.evan.voicesrs

/**
 * A tap or a spoken letter on an unrevealed multiple-choice row.
 * The letter is the one printed on the row, which stays the original letter
 * when the rows are shuffled. This never grades and never advances.
 */
object TapAnswer {
    data class Choice(
        val letter: String,
        val correct: Boolean,
        /** 3 is Good, 1 is Again. A hint for the chip outline only. */
        val suggestedEase: Int,
        val autoGrade: Boolean = false,
        val stillTappable: Boolean = false
    )

    private val words = mapOf(
        "a" to "A", "ay" to "A", "eh" to "A",
        "b" to "B", "be" to "B", "bee" to "B",
        "c" to "C", "see" to "C", "sea" to "C", "cee" to "C",
        "d" to "D", "dee" to "D",
        "e" to "E", "ee" to "E",
        "f" to "F", "ef" to "F", "eff" to "F"
    )

    private val prefixed = Regex(
        """^(?:answer|option|choice|letter)(?:\s+is)?\s+([a-z]+)$"""
    )

    fun tappable(mcq: Boolean, revealed: Boolean): Boolean = mcq && !revealed

    /** Whole utterance is a letter, "option B", or "bee". Anything else is not a choice. */
    fun parseSpoken(raw: String?, letters: Set<String>): String? {
        if (raw.isNullOrBlank() || letters.isEmpty()) return null
        val allowed = letters.map { it.uppercase() }.toSet()
        var text = raw.trim().lowercase()
            .replace(Regex("[.!?,]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.startsWith("the ")) text = text.removePrefix("the ").trim()
        val token = prefixed.matchEntire(text)?.groupValues?.get(1) ?: text
        val letter = words[token] ?: return null
        return letter.takeIf { it in allowed }
    }

    fun onTap(letter: String, correct: String?, revealed: Boolean, mcq: Boolean = true): Choice? {
        if (!tappable(mcq, revealed)) return null
        val clean = letter.trim().uppercase()
        if (clean.length != 1 || clean[0] !in 'A'..'F') return null
        return choice(clean, correct)
    }

    fun onSpoken(raw: String?, letters: Set<String>, correct: String?, revealed: Boolean): Choice? {
        if (revealed) return null
        val letter = parseSpoken(raw, letters) ?: return null
        return choice(letter, correct)
    }

    fun suggestedEase(letter: String, correct: String?): Int {
        return if (correct != null && letter.equals(correct, ignoreCase = true)) 3 else 1
    }

    /** Thread label. A letter choice reads "YOUR ANSWER: B"; anything else keeps the old line. */
    fun heading(transcript: String): String {
        val letter = parseSpoken(transcript, ('A'..'F').map { it.toString() }.toSet())
        return if (letter != null) "YOUR ANSWER: $letter" else QuestionCard.ANSWER_RATING
    }

    private fun choice(letter: String, correct: String?): Choice {
        val right = correct != null && letter.equals(correct, ignoreCase = true)
        return Choice(
            letter = letter.uppercase(),
            correct = right,
            suggestedEase = if (right) 3 else 1,
            autoGrade = false,
            stillTappable = false
        )
    }
}
