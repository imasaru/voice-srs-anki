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
        val selectedEase: Int?
    ) : ChatRow() {
        override val key: String = "live"
    }
}

object ChatFeed {
    fun rows(turns: List<ReviewTurn>, state: DrillUiState): List<ChatRow> {
        val out = ArrayList<ChatRow>(turns.size + 2)
        for (turn in turns) out += ChatRow.Past(turn)
        if (!state.drillRunning || state.frontHtml.isBlank()) return out
        val controls = AnswerControls.of(
            silent = state.silentSession,
            hasQuestion = true,
            answerShown = state.showBack,
            voiceRating = state.ratingVisible
        )
        out += ChatRow.Divider
        out += ChatRow.Live(
            front = state.frontHtml,
            back = state.backHtml,
            showBack = state.showBack && state.backHtml.isNotBlank(),
            heard = state.heard,
            showAnswer = controls.showAnswer,
            rating = controls.rating,
            selectedEase = if (controls.rating) state.selectedEase else null
        )
        return out
    }
}
