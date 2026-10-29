package com.evan.voicesrs

/**
 * h83: own words on + "Listen for letter answers" off. Spoken A-D are not
 * matched (taps still are) and only "skip" / "skip card" skips.
 */
object OwnWordsMode {
    private val STRICT_SKIP = setOf("skip", "skip card")
    private val PREFIXED = Regex("^(answer|option) ([a-z]+)$")

    private fun clean(raw: String?): String =
        raw.orEmpty().lowercase().replace(Regex("[^a-z ]"), " ").trim().replace(Regex("\\s+"), " ")

    /** True for the whole-utterance form "answer X" / "option X" (X one word). */
    fun isPrefixedForm(raw: String?): Boolean = PREFIXED.matches(clean(raw))

    /**
     * "answer B" / "option bee" as the WHOLE final utterance -> that letter.
     * Partials never pick. "answer the" is not D (only "answer d"/"answer dee").
     */
    fun prefixedLetter(raw: String?, letters: Set<String>?, partial: Boolean): String? {
        if (partial || letters.isNullOrEmpty()) return null
        val word = PREFIXED.matchEntire(clean(raw))?.groupValues?.get(2) ?: return null
        if (word == "the") return null
        return SpokenLetter.normalise(word, letters)
    }

    fun listensForLetters(ownWords: Boolean, letterListen: Boolean): Boolean = !ownWords || letterListen

    fun spokenLetters(letters: Set<String>?, ownWords: Boolean, letterListen: Boolean): Set<String>? =
        letters?.takeIf { listensForLetters(ownWords, letterListen) }

    fun isStrictSkip(raw: String?): Boolean {
        return clean(raw) in STRICT_SKIP
    }

    fun answerAction(raw: String?, lettersOn: Boolean): VoiceAction {
        if (!lettersOn && isPrefixedForm(raw)) return VoiceAction.None
        val action = GradeMapper.parse(raw)
        if (!lettersOn && action == VoiceAction.Skip && !isStrictSkip(raw)) return VoiceAction.None
        return action
    }

    fun thinkHear(raw: String?, lettersOn: Boolean): ThinkTime.Hear {
        if (!lettersOn && isPrefixedForm(raw)) return ThinkTime.Hear.ANSWER
        val hear = ThinkTime.classify(raw)
        if (!lettersOn && hear == ThinkTime.Hear.SKIP && !isStrictSkip(raw)) return ThinkTime.Hear.ANSWER
        return hear
    }
}
