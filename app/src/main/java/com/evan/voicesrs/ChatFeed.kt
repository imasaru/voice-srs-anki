package com.evan.voicesrs

/**
 * Rows of the right-hand chat, oldest first. [ChatPane] uses stackFromEnd so
 * the live question sits at the bottom. There is no pinned card above the list.
 */
sealed class ChatRow {
    abstract val key: String

    data class Past(val turn: ReviewTurn) : ChatRow() {
        override val key: String = "t${turn.turnId}"
    }

    data object Divider : ChatRow() {
        override val key: String = "new"
    }

    data class Live(
        val front: String,
        val back: String,
        val showBack: Boolean,
        val heard: String,
        val showAnswer: Boolean,
        val rating: Boolean,
        val selectedEase: Int?,
        /** False when Settings hides the card text. Buttons stay on the bar. */
        val revealText: Boolean = true
    ) : ChatRow() {
        override val key: String = "live"
    }
}

object ChatFeed {
    /**
     * [embedControls] is false on the narrow layout, where Show answer and the
     * rating buttons live in the fixed bottom bar. The row still shows the card.
     */
    fun rows(
        turns: List<ReviewTurn>,
        state: DrillUiState,
        embedControls: Boolean = true
    ): List<ChatRow> {
        val out = ArrayList<ChatRow>(turns.size + 2)
        for (turn in turns) out += ChatRow.Past(turn)
        if (!state.drillRunning || state.frontHtml.isBlank()) return out
        val controls = AnswerControls.of(
            hasQuestion = true,
            answerShown = state.showBack,
            silent = state.silentSession
        )
        val buttons = embedControls
        out += ChatRow.Divider
        out += ChatRow.Live(
            front = state.frontHtml,
            back = state.backHtml,
            showBack = state.showBack && state.backHtml.isNotBlank(),
            heard = state.heard,
            showAnswer = buttons && controls.showAnswer,
            rating = buttons && controls.rating,
            selectedEase = if (buttons && controls.rating) state.selectedEase else null
        )
        return out
    }
}
