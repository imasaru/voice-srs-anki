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
        val autoAcceptStep: Int? = null,
        val followUps: List<FollowUp> = emptyList(),
        val questionLabel: String = "",
        val correctField: String = "",
        val noteId: Long? = null,
        val memoFront: String = "",
        val memoBack: String = "",
        val memoAvailable: Boolean = false,
        val memoHint: Boolean = false,
        val guide: Int = 0 // SkipQueue.guide(true, showBack, silent) when embedded; 0 when narrow
    ) : ChatRow() {
        override val key: String = "live"
    }

    /** A question with no card. Stands alone in the thread. */
    data class Ask(
        val id: Long,
        val question: String,
        val state: ExplState,
        val deck: String = "",
        /** Wall clock when the ask was created. 0 until wired; rows order by this. */
        val at: Long = 0L
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
        notice: String? = null,
        history: List<StoredChat> = emptyList()
    ): List<ChatRow> {
        val liveTurnIds = turns.map { it.turnId }.toSet()
        val liveAskIds = asks.map { it.id }.toSet()
        val out = ArrayList<ChatRow>(turns.size + asks.size + history.size + 3)
        for (item in history) {
            when (item.kind) {
                StoredChat.TURN -> if (item.id !in liveTurnIds) out += ChatRow.Past(item.toTurn())
                StoredChat.ASK -> if (item.id !in liveAskIds) out += item.toAsk()
            }
        }
        // Merge live turns and asks by wall-clock time so a pinned study-report
        // Ask does not sit below every live turn. Turns rank before asks at equal
        // `at`; stable sort keeps at==0 rows in their original order (turns first).
        val keyed = ArrayList<Triple<Long, Int, ChatRow>>(turns.size + asks.size)
        for (turn in turns) keyed += Triple(turn.at, 0, ChatRow.Past(turn))
        for (ask in asks) keyed += Triple(ask.at, 1, ask)
        keyed.sortWith(compareBy({ it.first }, { it.second }))
        for (k in keyed) out += k.third
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
            guide = if (buttons) SkipQueue.guide(true, state.showBack, state.silentSession) else 0,
            rating = buttons && controls.rating,
            selectedEase = if (buttons && controls.rating) state.selectedEase else null,
            questionOrdinal = state.questionOrdinal,
            cardId = state.cardId,
            chosenLetter = state.chosenLetter,
            suggestedEase = if (buttons && controls.rating) state.suggestedEase else null,
            autoAcceptStep = if (buttons && controls.rating) state.autoAcceptStep else null,
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
