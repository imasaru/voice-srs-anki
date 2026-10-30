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
        return looseSpan(shown, words) ?: alignedSpan(shown, sentence)?.first
    }

    /** Exact or symbol-tolerant match only (no paraphrase alignment). */
    fun strictSpanIn(shown: String, sentence: String): IntRange? {
        val words = sentence.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        Regex(words.joinToString("\\s+") { Regex.escape(it) }).find(shown)?.range?.let { return it }
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

    /** h100: [spoken] from the start of sentence [index] (SentenceSplit ranges); unknown/out of range -> all of it. */
    fun resumeText(spoken: String, index: Int): String {
        val ranges = SentenceSplit.ranges(spoken)
        if (index <= 0 || index >= ranges.size) return spoken
        return spoken.substring(ranges[index].first)
    }

    private data class Tok(val w: String, val range: IntRange)

    private val TOKEN = Regex("[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\\p{L}+|\\p{N}+|==|=|\\*")
    private val SPOKEN_ONLY = setOf("dot", "underscore", "slash", "dash", "hyphen", "colon", "comma", "option", "open", "close", "paren", "bracket", "quote")

    private fun tokens(text: String): List<Tok> = TOKEN.findAll(text).map {
        val w = when (it.value) { "=", "==" -> "equals"; "*" -> "star"; else -> it.value.lowercase() }
        Tok(w, it.range)
    }.toList()

    private fun norm(w: String): String = when (w) {
        "equal", "equals" -> "equals"; "asterisk", "star", "all" -> "star"
        else -> if (w.length > 3 && w.endsWith("s")) w.dropLast(1) else w
    }

    /**
     * h100: word-level local alignment for code read as a paraphrase (Q26: "on target customer id
     * equals source customer id" for `ON target.customer_id = source.customer_id`). Identifiers split
     * on _ . and camelCase, = is "equals", * is "star"; spoken-only words (dot, option…) are dropped.
     * Returns the shown span and its score, or null when under 60% of the spoken words line up.
     */
    fun alignedSpan(shown: String, sentence: String): Pair<IntRange, Int>? {
        val s = tokens(shown)
        val p = tokens(sentence).filter { it.w !in SPOKEN_ONLY }.map { norm(it.w) }
        if (s.isEmpty() || p.size < 2) return null
        val sn = s.map { norm(it.w) }
        val n = s.size; val m = p.size
        val h = Array(n + 1) { IntArray(m + 1) }
        var best = 0; var bi = 0; var bj = 0
        for (i in 1..n) for (j in 1..m) {
            val diag = h[i - 1][j - 1] + if (sn[i - 1] == p[j - 1]) 3 else -1
            val v = maxOf(0, diag, h[i - 1][j] - 1, h[i][j - 1] - 1)
            h[i][j] = v
            if (v > best) { best = v; bi = i; bj = j }
        }
        if (best == 0) return null
        var i = bi; var j = bj; var matches = 0; var first = -1; val last = bi - 1
        while (i > 0 && j > 0 && h[i][j] > 0) {
            val v = h[i][j]
            if (v == h[i - 1][j - 1] + (if (sn[i - 1] == p[j - 1]) 3 else -1)) {
                if (sn[i - 1] == p[j - 1]) { matches++; first = i - 1 }
                i--; j--
            } else if (v == h[i - 1][j] - 1) i-- else j--
        }
        if (first < 0 || matches < 2 || matches * 10 < m * 6) return null
        return (s[first].range.first..s[last].range.last) to best
    }
}
