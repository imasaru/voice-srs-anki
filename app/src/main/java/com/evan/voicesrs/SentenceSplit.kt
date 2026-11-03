package com.evan.voicesrs

/** Pure sentence splitter for reading highlight. No Android. Ranges are char offsets into the original text, trimmed of whitespace. */
object SentenceSplit {
    private val ABBREV = setOf("e.g.", "i.e.", "vs.", "mr.", "mrs.", "dr.")

    /** Sentence ranges. A sentence ends at . ! ? followed by whitespace or end of text (not after a known abbreviation), at ！？, or at a newline. Blank pieces are dropped. On a choice card (A. and B. markers) a lone letter A..H plus dot starts a new sentence instead of ending one. */
    fun ranges(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        val choices = hasChoices(text)
        fun emit(endExcl: Int) {
            var s = start
            var e = endExcl
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (e > s) out.add(s until e)
        }
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\n') {
                emit(i); start = i + 1
            } else if (ch in "。！？") {
                emit(i + 1); start = i + 1
            } else if (choices && ch == '.' && isChoiceLetter(text, i)) {
                emit(i - 1); start = i - 1
            } else if (ch in ".!?" && (i + 1 == text.length || text[i + 1].isWhitespace()) && !isAbbrev(text, i)) {
                emit(i + 1); start = i + 1
            }
            i++
        }
        emit(text.length)
        return out
    }

    /** The sentence strings for [text], in order. */
    fun sentences(text: String): List<String> = ranges(text).map { text.substring(it) }

    /** Index of the sentence containing char [offset]; an offset in the gap between sentences maps to the next one. Negative or past the last sentence -> -1. */
    fun indexAt(ranges: List<IntRange>, offset: Int): Int {
        if (offset < 0) return -1
        return ranges.indexOfFirst { offset <= it.last }
    }

    private fun isAbbrev(text: String, dot: Int): Boolean {
        if (text[dot] != '.') return false
        var s = dot
        while (s > 0 && !text[s - 1].isWhitespace()) s--
        return text.substring(s, dot + 1).trimStart('(', '"', '\'').lowercase() in ABBREV
    }

    /** True when [text] has choice markers: an "A." token at the start or after whitespace, and a "B." token after whitespace. */
    private fun hasChoices(text: String): Boolean = Regex("(^|\\s)A\\.\\s").containsMatchIn(text) && Regex("\\sB\\.(\\s|$)").containsMatchIn(text)

    /** The '.' at [dot] ends a lone choice letter A..H that starts a word and is followed by whitespace or end of text. */
    private fun isChoiceLetter(text: String, dot: Int): Boolean = dot >= 1 && text[dot - 1] in 'A'..'H' && (dot == 1 || text[dot - 2].isWhitespace()) && (dot + 1 == text.length || text[dot + 1].isWhitespace())
}
