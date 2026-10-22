package com.evan.voicesrs

/**
 * Question card for the chat and the thread.
 * Option lines matching `^[A-F][.)]` become their own rows. Anything else is
 * ordinary text. The correct row is marked only after the answer is revealed.
 */
object QuestionCard {
    data class Option(val letter: String, val text: String, val correct: Boolean = false)

    data class Parsed(val stem: String, val options: List<Option>, val mcq: Boolean)

    private val optionLine = Regex("""^([A-F])[.)]\s*(\S.*)?$""")

    const val ANSWER_RATING = "YOUR ANSWER · RATING"

    fun label(n: Int): String = "QUESTION $n"

    fun parse(plain: String): Parsed {
        val lines = plain.replace("\r\n", "\n").replace('\r', '\n')
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
                options += Option(match.groupValues[1].uppercase(), match.groupValues[2].trim())
            } else if (!started) {
                stem += line
            } else {
                trailing += line
            }
        }
        if (options.size < 2) {
            return Parsed(lines.joinToString("\n"), emptyList(), mcq = false)
        }
        val stemText = (stem + trailing).joinToString("\n").trim()
        return Parsed(stemText, options, mcq = true)
    }

    fun withCorrect(parsed: Parsed, answerPlain: String, revealed: Boolean): Parsed {
        if (!parsed.mcq || !revealed) {
            return parsed.copy(options = parsed.options.map { it.copy(correct = false) })
        }
        val letter = correctLetter(answerPlain, parsed.options) ?: return parsed
        return parsed.copy(options = parsed.options.map { it.copy(correct = it.letter == letter) })
    }

    /**
     * Letter the answer names, if it names one of [options].
     * A leading `B` or `B.` wins. Otherwise a single option whose text is contained.
     */
    fun correctLetter(answerPlain: String, options: List<Option>): String? {
        if (options.isEmpty()) return null
        val text = answerPlain.trim()
        if (text.isEmpty()) return null
        val letters = options.map { it.letter }.toSet()
        val lead = Regex("""^([A-F])\b""").find(text.uppercase())
        if (lead != null && lead.groupValues[1] in letters) return lead.groupValues[1]
        val named = Regex("""(?i)\b(?:answer|option|choice)\s*(?:is|:)?\s*([A-F])\b""").find(text)
        if (named != null) {
            val letter = named.groupValues[1].uppercase()
            if (letter in letters) return letter
        }
        val lower = text.lowercase()
        val hits = options.filter { opt ->
            opt.text.length >= 3 && lower.contains(opt.text.lowercase())
        }
        return if (hits.size == 1) hits[0].letter else null
    }

    fun optionRow(option: Option): String {
        val line = "${option.letter}. ${option.text}".trim()
        return if (option.correct) "$line ✓" else line
    }

    /** Quoted spoken answer, or the italic placeholder when nothing was said. */
    fun quotedAnswer(transcript: String): Pair<String, Boolean> {
        val bubble = BubbleModel.userBubble(transcript, null)
        return if (bubble.answered) "\"${bubble.answer}\"" to true else bubble.answer to false
    }
}
