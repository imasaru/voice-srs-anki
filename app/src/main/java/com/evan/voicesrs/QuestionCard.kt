package com.evan.voicesrs

/**
 * Question card for the chat and the thread.
 * Option lines matching `^[A-F][.)]` become their own rows. Anything else is
 * ordinary text. The correct row is marked only after the answer is revealed.
 */
object QuestionCard {
    data class Option(
        val letter: String,
        val text: String,
        val correct: Boolean = false,
        /** The learner picked this row and it is not the correct one. */
        val chosen: Boolean = false
    )

    data class Parsed(val stem: String, val options: List<Option>, val mcq: Boolean)

    private val optionLine = Regex("""^([A-F])[.)]\s*(\S.*)?$""")

    const val ANSWER_RATING = "YOUR ANSWER · RATING"

    /** Session count when the card text has no number of its own. */
    fun label(n: Int): String = QuestionLabel.resolve("", n).label

    fun parse(plain: String): Parsed {
        val (source, slots) = maskPre(plain)
        val lines = source.replace("\r\n", "\n").replace('\r', '\n')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val stem = ArrayList<String>()
        val options = ArrayList<Option>()
        val trailing = ArrayList<String>()
        var started = false
        for (line in lines) {
            val match = optionLine.matchEntire(line)
            if (match != null) {
                started = true
                options += Option(
                    match.groupValues[1].uppercase(),
                    restorePre(match.groupValues[2].trim(), slots)
                )
            } else if (!started) {
                stem += restorePre(line, slots)
            } else {
                trailing += restorePre(line, slots)
            }
        }
        if (options.size < 2) {
            return Parsed(lines.joinToString("\n") { restorePre(it, slots) }, emptyList(), mcq = false)
        }
        val stemText = (stem + trailing).joinToString("\n").trim()
        return Parsed(stemText, options, mcq = true)
    }

    /** Newlines inside `<pre>` are not option lines. No `<pre>` returns [plain] unchanged. */
    private fun maskPre(plain: String): Pair<String, List<String>> {
        if (!plain.contains("<pre", ignoreCase = true)) return plain to emptyList()
        val slots = ArrayList<String>()
        val masked = Regex("(?is)<pre\\b[^>]*>.*?</pre>").replace(plain) { m ->
            val id = slots.size
            slots += m.value
            "\uE000$id\uE001"
        }
        return masked to slots
    }

    private fun restorePre(line: String, slots: List<String>): String {
        if (slots.isEmpty()) return line
        var out = line
        for (i in slots.indices) out = out.replace("\uE000$i\uE001", slots[i])
        return out
    }

    fun withCorrect(parsed: Parsed, answerPlain: String, revealed: Boolean): Parsed {
        if (!parsed.mcq || !revealed) {
            return parsed.copy(options = parsed.options.map { it.copy(correct = false) })
        }
        val letter = correctLetter(answerPlain, parsed.options) ?: return parsed
        return parsed.copy(options = parsed.options.map { it.copy(correct = it.letter == letter) })
    }

    /**
     * Letter the note's Correct field (or the card back) names, if it names
     * one of [options]. A bare `B` / `B.` / `B)` wins. Then `Answer B` /
     * `Correct: B`. Then a single option whose text is contained. A leading
     * `A` that starts a sentence ("A Spark DataFrame…") is not a letter.
     */
    fun correctLetter(answerPlain: String, options: List<Option>): String? {
        if (options.isEmpty()) return null
        val text = answerPlain.trim()
        if (text.isEmpty()) return null
        val letters = options.map { it.letter }.toSet()
        val bare = Regex("""^([A-F])[.)]?\s*$""").find(text.uppercase())
        if (bare != null && bare.groupValues[1] in letters) return bare.groupValues[1]
        val named = Regex("""(?i)\b(?:answer|option|choice|correct)\s*(?:is|:)?\s*([A-F])\b""").find(text)
        if (named != null) {
            val letter = named.groupValues[1].uppercase()
            if (letter in letters) return letter
        }
        val lower = text.lowercase()
        val hits = options.filter { opt ->
            val plain = AnkiText.stripHtml(opt.text)
            plain.length >= 3 && lower.contains(plain.lowercase())
        }
        if (hits.size == 1) return hits[0].letter
        val lead = Regex("""(?s)^([A-F])[.)]\s*(.*)""").find(text.uppercase()) ?: return null
        val letter = lead.groupValues[1]
        if (letter !in letters) return null
        val rest = lead.groupValues[2].trim()
        if (rest.isEmpty()) return letter
        val opt = options.first { it.letter == letter }
        val plain = AnkiText.stripHtml(opt.text).trim()
        if (plain.isNotEmpty() && rest.lowercase().startsWith(plain.lowercase())) return letter
        return null
    }

    /** Prefer the Correct field; fall back to the card back. */
    fun highlightSource(correctField: String, back: String): String {
        val field = correctField.trim()
        if (field.isNotEmpty()) return field
        return back.trim()
    }

    /**
     * Mark the correct row after reveal, and the chosen wrong row.
     * A correct choice gets only the check. Rows stay in [parsed] order.
     */
    fun present(parsed: Parsed, answerPlain: String, revealed: Boolean, chosen: String?): Parsed {
        val marked = withCorrect(parsed, answerPlain, revealed)
        if (!revealed || chosen.isNullOrBlank()) return marked
        val pick = chosen.trim().uppercase()
        return marked.copy(
            options = marked.options.map { opt ->
                opt.copy(chosen = !opt.correct && opt.letter.equals(pick, ignoreCase = true))
            }
        )
    }

    fun optionRow(option: Option): String {
        val line = "${option.letter}. ${option.text}".trim()
        return when {
            option.correct -> "$line ✓"
            option.chosen -> "$line ✗"
            else -> line
        }
    }

    /** Quoted spoken answer, or the italic placeholder when nothing was said. */
    fun quotedAnswer(transcript: String): Pair<String, Boolean> {
        val bubble = BubbleModel.userBubble(transcript, null)
        return if (bubble.answered) "\"${bubble.answer}\"" to true else bubble.answer to false
    }
}
