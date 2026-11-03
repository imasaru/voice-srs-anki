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

    fun tappable(mcq: Boolean, revealed: Boolean): Boolean = mcq && !revealed

    /**
     * Whether tapping the current card should cut the TTS that is reading it.
     * Only when the drill is running and neither the chat is paused nor the
     * session is silent; anything else leaves speech alone.
     */
    fun cutsSpeech(drillRunning: Boolean, chatPaused: Boolean, silentSession: Boolean): Boolean =
        drillRunning && !chatPaused && !silentSession

    /**
     * After a readout was cut: read it again only when Silent cut it, not when
     * a card tap skipped it ([cutsSpeech]).
     */
    fun rereadAfterCut(interrupted: Boolean, skipped: Boolean): Boolean = interrupted && !skipped

    /**
     * Whole utterance is a letter on this card. Homophones, NATO words, and
     * ordinals live in [SpokenLetter]. Ordinals are the original letters
     * (first = A), including after a shuffle. "the" alone is D. "tea" is not.
     */
    fun parseSpoken(raw: String?, letters: Set<String>): String? = SpokenLetter.normalise(raw, letters)

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

    /** h107b3: spoken result for a letter choice; [ease] lights the grade chip. */
    data class LetterResult(val ease: Int, val line: String)

    fun letterResult(letter: String, correct: String): LetterResult =
        if (letter.trim().equals(correct.trim(), ignoreCase = true)) LetterResult(3, "Correct.")
        else LetterResult(1, "Not quite. The answer is ${correct.trim().uppercase()}.")

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
