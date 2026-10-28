package com.evan.voicesrs

/** Sentence-by-sentence speech for the drill and the published current sentence. Pure, no Android. */
object SentenceReading {
    /** The pieces [spoken] is spoken in: SentenceSplit sentences, or the whole text when splitting finds none. Blank -> empty. */
    fun parts(spoken: String): List<String> {
        if (spoken.isBlank()) return emptyList()
        return SentenceSplit.sentences(spoken).ifEmpty { listOf(spoken.trim()) }
    }

    /** DrillUi.update block: sentence [index] with text [sentence] is being spoken. */
    fun at(index: Int, sentence: String): (DrillUiState) -> DrillUiState =
        { it.copy(readingSentence = sentence, readingIndex = index) }

    /** DrillUi.update block: nothing is being spoken. */
    fun cleared(): (DrillUiState) -> DrillUiState =
        { it.copy(readingSentence = "", readingIndex = -1) }

    /** Where [sentence] appears in the on-screen text [shown], matching its words in order with any whitespace between. Not found or blank -> null. */
    fun spanIn(shown: String, sentence: String): IntRange? {
        val words = sentence.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val pattern = Regex(words.joinToString("\\s+") { Regex.escape(it) })
        return pattern.find(shown)?.range
    }
}
