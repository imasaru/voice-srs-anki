package com.evan.voicesrs

/**
 * Spoken single-letter answers on an unrevealed multiple-choice card.
 *
 * Ordinals name the original printed letters, not the row after a shuffle.
 * "first" is A and "sixth" is F. [OptionShuffle] moves the rows and leaves
 * the letter on the text, and a tap uses that same letter. A spoken ordinal
 * therefore picks the letter printed on the row, which is the letter grading
 * already uses. "second" is rejected when the card has no B, even if B's text
 * is showing first.
 *
 * "the" is D only when that word is the whole utterance (or the word after
 * "option" / "answer" / "letter"). "the bee" stays B. A sentence that merely
 * contains "the" is not a choice.
 *
 * "tea" is not D. It is an ordinary word, and treating it as a letter would
 * select an option when someone names the drink or when a longer phrase is
 * mis-heard. "dee" and a bare "the" already cover the usual recognizer output.
 */
object SpokenLetter {
    const val MAX_RESULTS = 5
    const val LANGUAGE = "en-US"
    /** h99: COMPLETE_SILENCE_LENGTH extra as Int; Google's service logged "expected Integer but value
     * was a java.lang.Long. The default value 0 was returned." on every listen. */
    fun silenceExtra(ms: Long): Int = ms.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    /** The offline model drops bare letters. Online recognition stays preferred. */
    const val PREFER_OFFLINE = false
    const val HEARD_PREFIX = "Heard:"

    sealed class Decision {
        /** Stop this listen and take the tap-to-answer path. */
        data class Match(val letter: String) : Decision()

        /**
         * A hypothesis came back, or the recognizer closed after speech, and it
         * is not a letter on this card. [status] is null when there is no text
         * to quote. This counts as one listen attempt and must not silence-restart.
         */
        data class Unmatched(val heard: String) : Decision() {
            val status: String? get() = heard.trim().takeIf { it.isNotEmpty() }?.let { statusLine(it) }
        }

        /** Nobody spoke. The caller may silence-restart while time remains. */
        data object Empty : Decision()
    }

    data class Step(
        /** True when this utterance must not open another recognizer. */
        val stop: Boolean,
        /** Attempts used after this result. Only [Decision.Unmatched] increments. */
        val attempts: Int,
        /** Attempts are used up. The caller switches to silent and does not grade. */
        val giveUp: Boolean,
        val letter: String? = null,
        val status: String? = null
    )

    private val words = mapOf(
        "a" to "A", "ay" to "A", "eh" to "A", "alpha" to "A", "first" to "A",
        "b" to "B", "be" to "B", "bee" to "B", "bea" to "B", "bravo" to "B", "second" to "B",
        "c" to "C", "see" to "C", "sea" to "C", "si" to "C", "she" to "C", "cee" to "C",
        "charlie" to "C", "third" to "C",
        "d" to "D", "dee" to "D", "the" to "D", "delta" to "D", "fourth" to "D",
        "e" to "E", "ee" to "E", "echo" to "E", "fifth" to "E",
        "f" to "F", "ef" to "F", "eff" to "F", "foxtrot" to "F", "sixth" to "F"
    )

    private val prefixed = Regex(
        """^(?:answer|option|choice|letter|hey)(?:\s+is)?\s+([a-z]+)$"""
    )

    fun statusLine(heard: String): String = "$HEARD_PREFIX \"$heard\""

    /**
     * Whole utterance is one letter on [letters]. Anything else is null.
     * [partial] is true for a recognizer partial: a bare "the" there is the
     * start of a sentence, not D, so it is null. Finals keep "the" as D.
     */
    fun normalise(raw: String?, letters: Set<String>, partial: Boolean = false): String? {
        if (raw.isNullOrBlank() || letters.isEmpty()) return null
        val allowed = letters.map { it.uppercase() }.toSet()
        var text = raw.trim().lowercase()
            .replace(Regex("[.!?,]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        // A partial "the" is usually the first word of a sentence, not D.
        if (partial && text == "the") return null
        // Filler before a letter ("the bee"). A bare "the" is the letter D.
        if (text != "the" && text.startsWith("the ")) text = text.removePrefix("the ").trim()
        val token = prefixed.matchEntire(text)?.groupValues?.get(1) ?: text
        val letter = words[token] ?: return null
        return letter.takeIf { it in allowed }
    }

    /**
     * First letter on the card in [finals] (best first), then in [partials].
     * Blank hypotheses are skipped. A later line can match when the top one cannot.
     */
    fun match(finals: List<String?>, partials: List<String?>, letters: Set<String>): String? {
        for (raw in finals) {
            val letter = normalise(raw, letters)
            if (letter != null) return letter
        }
        for (raw in partials) {
            val letter = normalise(raw, letters, partial = true)
            if (letter != null) return letter
        }
        return null
    }

    fun decide(
        finals: List<String?>,
        partials: List<String?>,
        letters: Set<String>,
        speechDetected: Boolean = false
    ): Decision {
        val letter = match(finals, partials, letters)
        if (letter != null) return Decision.Match(letter)
        val heard = display(finals, partials)
        if (heard != null) return Decision.Unmatched(heard)
        if (speechDetected) return Decision.Unmatched("")
        return Decision.Empty
    }

    fun stopsForMatch(decision: Decision): Boolean = decision is Decision.Match

    fun countsAttempt(decision: Decision): Boolean = decision is Decision.Unmatched

    /** Silence restart is only for a window that produced no speech and no text. */
    fun maySilenceRestart(decision: Decision): Boolean = decision is Decision.Empty

    fun advance(decision: Decision, completed: Int, maxAttempts: Int): Step {
        return when (decision) {
            is Decision.Match -> Step(
                stop = true,
                attempts = completed,
                giveUp = false,
                letter = decision.letter
            )
            is Decision.Unmatched -> {
                val next = completed + 1
                val giveUp = ListenWindow.gaveUp(next, maxAttempts, lastMissed = true)
                Step(
                    stop = giveUp,
                    attempts = next,
                    giveUp = giveUp,
                    status = decision.status
                )
            }
            Decision.Empty -> Step(stop = false, attempts = completed, giveUp = false)
        }
    }

    /** Top final, otherwise the latest partial. Null when every line is blank. */
    private fun display(finals: List<String?>, partials: List<String?>): String? {
        for (raw in finals) {
            val text = raw?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        for (raw in partials.asReversed()) {
            val text = raw?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        return null
    }
}
