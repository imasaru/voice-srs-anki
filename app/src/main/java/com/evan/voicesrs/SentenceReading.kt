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
        pattern.find(shown)?.range?.let { return it }
        return looseSpan(shown, words)
    }

    /** Spoken symbol words the display shows as a character (spark dot executor = spark.executor). */
    private val SYMBOL = mapOf("dot" to ".", "slash" to "/", "underscore" to "_", "dash" to "-",
        "hyphen" to "-", "colon" to ":", "equals" to "=", "plus" to "+", "star" to "*", "at" to "@")
    private const val SEP = "[\\s.;,:()\\[\\]\"'/_=+*-]*"

    /** h99 fallback: case-insensitive, punctuation-insensitive word match, symbol words optional.
     * Q10: spoken "spark dot executor dot memory" vs shown `spark.executor.memory`; options read
     * with ". " where the card shows "; ". */
    private fun looseSpan(shown: String, words: List<String>): IntRange? {
        val cores = words.map { it.trim { c -> !c.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        if (cores.isEmpty()) return null
        val body = cores.joinToString(SEP) { w ->
            val sym = SYMBOL[w.lowercase()]
            if (sym != null) "(?:${Regex.escape(w)}|${Regex.escape(sym)})?" else Regex.escape(w)
        }
        val m = Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).find(shown) ?: return null
        return m.range
    }
}
