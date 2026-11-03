package com.evan.voicesrs

/** Where the spoken sentence is painted in a chat bubble. Pure, no Android. */
object ReadingSpan {
    /** Block [block] of the bubble, characters [range] of that block's text. */
    data class Pick(val block: Int, val range: IntRange)

    /** First block whose text contains [sentence] (SentenceReading.spanIn). Blank or no match -> null. */
    fun pick(blocks: List<String>, sentence: String): Pick? {
        if (sentence.isBlank()) return null
        for ((i, text) in blocks.withIndex()) {
            val range = SentenceReading.strictSpanIn(text, sentence) ?: continue
            return Pick(i, range)
        }
        // h100: no literal match (code read as a paraphrase): best-scoring aligned block.
        var best: Pick? = null; var bestScore = 0
        for ((i, text) in blocks.withIndex()) {
            val (range, score) = SentenceReading.alignedSpan(text, sentence) ?: continue
            if (score > bestScore) { best = Pick(i, range); bestScore = score }
        }
        return best
    }

    /** Index of the first [rows] entry where [sentence] is a strict sentence-span, else -1. Blank [sentence] -> -1. */
    fun pickOption(rows: List<String>, sentence: String): Int {
        if (sentence.isBlank()) return -1
        for ((i, row) in rows.withIndex()) {
            if (SentenceReading.strictSpanIn(row, sentence) != null) return i
        }
        return -1
    }

    /** True when the turn for [turnCardId] is the card the drill is reading now. */
    fun isCurrentTurn(turnCardId: Long, state: DrillUiState): Boolean =
        state.drillRunning && state.cardId == turnCardId && state.frontHtml.isNotBlank()

    /** Sentence to paint in the bubble of the turn for [turnCardId]: only the current drill turn, only when [highlightOn]; else "". */
    fun sentenceFor(turnCardId: Long, state: DrillUiState, highlightOn: Boolean): String =
        if (highlightOn && isCurrentTurn(turnCardId, state)) state.readingSentence else ""

    /** List position of the current drill card's turn: the LAST row whose card id matches. [rowCardIds] has null for non-turn rows. None -> -1. */
    fun currentTurnIndex(rowCardIds: List<Long?>, state: DrillUiState): Int {
        if (!state.drillRunning || state.frontHtml.isBlank()) return -1
        return rowCardIds.indexOfLast { it != null && it == state.cardId }
    }
}
