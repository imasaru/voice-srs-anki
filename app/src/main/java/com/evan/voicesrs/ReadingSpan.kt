package com.evan.voicesrs

/** Where the spoken sentence is painted in a chat bubble. Pure, no Android. */
object ReadingSpan {
    /** Block [block] of the bubble, characters [range] of that block's text. */
    data class Pick(val block: Int, val range: IntRange)

    /** First block whose text contains [sentence] (SentenceReading.spanIn). Blank or no match -> null. */
    fun pick(blocks: List<String>, sentence: String): Pick? {
        if (sentence.isBlank()) return null
        for ((i, text) in blocks.withIndex()) {
            val range = SentenceReading.spanIn(text, sentence) ?: continue
            return Pick(i, range)
        }
        return null
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
