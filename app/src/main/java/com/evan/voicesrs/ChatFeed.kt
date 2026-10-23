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
        val revealText: Boolean = true,
        /** 1-based session order. Past rows use their list index instead. */
        val questionOrdinal: Int = 0,
        val cardId: Long = 0L,
        val chosenLetter: String? = null,
        val suggestedEase: Int? = null,
        val followUps: List<FollowUp> = emptyList(),
        val questionLabel: String = "",
        val correctField: String = "",
        val noteId: Long? = null,
        val memoFront: String = "",
        val memoBack: String = "",
        val memoAvailable: Boolean = false,
        val memoHint: Boolean = false
    ) : ChatRow() {
        override val key: String = "live"
    }

    /** A question with no card. Stands alone in the thread. */
    data class Ask(
        val id: Long,
        val question: String,
        val state: ExplState,
        val deck: String = ""
    ) : ChatRow() {
        override val key: String = "ask$id"
    }

    data class Notice(val message: String) : ChatRow() {
        override val key: String = "notice"
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
        embedControls: Boolean = true,
        liveFollowUps: List<FollowUp> = emptyList(),
        asks: List<ChatRow.Ask> = emptyList(),
        notice: String? = null
    ): List<ChatRow> {
        val out = ArrayList<ChatRow>(turns.size + asks.size + 3)
        for (turn in turns) out += ChatRow.Past(turn)
        for (ask in asks) out += ask
        val note = notice?.trim().orEmpty()
        if (note.isNotEmpty()) out += ChatRow.Notice(note)
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
            selectedEase = if (buttons && controls.rating) state.selectedEase else null,
            questionOrdinal = state.questionOrdinal,
            cardId = state.cardId,
            chosenLetter = state.chosenLetter,
            suggestedEase = if (buttons && controls.rating) state.suggestedEase else null,
            followUps = liveFollowUps,
            questionLabel = state.questionLabel,
            correctField = state.correctField,
            noteId = state.noteId,
            memoFront = state.memoFront,
            memoBack = state.memoBack,
            memoAvailable = state.memoAvailable,
            memoHint = state.memoHint
        )
        return out
    }
}
