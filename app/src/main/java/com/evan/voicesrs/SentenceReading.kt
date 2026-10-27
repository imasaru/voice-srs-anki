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
}
