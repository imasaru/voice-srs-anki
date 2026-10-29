package com.evan.voicesrs

import java.util.concurrent.CopyOnWriteArrayList

/**
 * One graded card in the session chat. [ease] is fixed at grade time.
 * [unread] is memory only and is never written to the cache.
 */
data class ReviewTurn(
    val turnId: Long,
    val cardId: Long,
    val noteId: Long?,
    val frontText: String,
    val backText: String,
    val contentHash: String,
    val transcript: String,
    val ease: Int?,
    val explanation: ExplState,
    val followUps: List<FollowUp> = emptyList(),
    val unread: Boolean = false,
    /** QUESTION N or CARD N. Empty on turns recorded before v0.1.19. */
    val questionLabel: String = "",
    /** Note Correct field. Empty on turns recorded before v0.1.20. */
    val correctField: String = "",
    /** Think-to-grade duration in ms. 0 when unknown. */
    val durationMs: Long = ThinkTime.UNKNOWN_DURATION_MS,
    /** Wall clock when the turn was created. 0 until wired; rows order by this. */
    val at: Long = 0L,
    /** Own-words judge reason, saved with the turn. Empty on turns without a judge verdict (and before h84). */
    val judgeReason: String = "",
    /** Ease the judge gave; the reason is shown only while [ease] equals it. Null when no reason. */
    val judgeEase: Int? = null
)

sealed interface ExplState {
    data object None : ExplState
    data object Queued : ExplState
    data object Pending : ExplState
    data class Streaming(val partial: String, val thinking: Boolean = false) : ExplState
    data class Ready(val text: String, val cached: Boolean) : ExplState
    data class Error(val message: String, val retryable: Boolean) : ExplState
}

data class FollowUp(
    val q: String,
    val state: ExplState,
    val at: Long = 0L,
    val replyAt: Long = 0L
)

/**
 * Session review thread. Same listener shape as [DrillUi], so it survives
 * activity recreation. A new drill session calls [clear], which drops unread.
 * A turn is appended only when [ease] is already final.
 */
object ReviewLog {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(List<ReviewTurn>) -> Unit>()
    private var turns: List<ReviewTurn> = emptyList()
    private var nextId = 1L

    fun addListener(listener: (List<ReviewTurn>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<ReviewTurn>) -> Unit) {
        listeners.remove(listener)
    }

    fun snapshot(): List<ReviewTurn> = synchronized(lock) { turns }

    fun unreadCount(): Int = synchronized(lock) { turns.count { it.unread } }

    /** New drill session. Unread does not carry over, and turn ids keep climbing. */
    fun clear() {
        synchronized(lock) { turns = emptyList() }
        dispatch()
    }

    /**
     * Append after a grade. [ease] null means the card was not graded, and
     * nothing is recorded. A cache hit is [ExplState.Ready] with unread false.
     * A miss is [ExplState.Queued], also unread false: the badge arrives only
     * when a fresh generation finishes.
     */
    fun appendAfterGrade(
        ease: Int?,
        cardId: Long,
        noteId: Long?,
        frontText: String,
        backText: String,
        contentHash: String,
        transcript: String,
        cachedText: String?,
        aiEnabled: Boolean,
        autoExplain: Boolean = true,
        questionLabel: String = "",
        correctField: String = "",
        durationMs: Long = ThinkTime.UNKNOWN_DURATION_MS
    ): ReviewTurn? {
        if (ease == null) return null
        val explanation = when {
            !aiEnabled -> ExplState.None
            cachedText != null -> ExplState.Ready(cachedText, cached = true)
            autoExplain -> ExplState.Queued
            else -> ExplState.None
        }
        val judgeReason = JudgeNote.take(cardId, ease)
        val turn = synchronized(lock) {
            val created = ReviewTurn(
                turnId = nextId++,
                cardId = cardId,
                noteId = noteId,
                frontText = frontText,
                backText = backText,
                contentHash = contentHash,
                transcript = transcript,
                ease = ease,
                explanation = explanation,
                unread = false,
                questionLabel = questionLabel,
                correctField = correctField,
                durationMs = durationMs,
                at = System.currentTimeMillis(),
                judgeReason = judgeReason.orEmpty(),
                judgeEase = if (judgeReason != null) ease else null
            )
            turns = turns + created
            created
        }
        persist(turn)
        dispatch()
        return turn
    }

    fun markQueued(turnId: Long) = replace(turnId) { turn ->
        if (turn.explanation is ExplState.Ready) turn else turn.copy(explanation = ExplState.Queued)
    }

    fun markPending(turnId: Long) = replace(turnId) { turn ->
        if (turn.explanation is ExplState.Ready) turn else turn.copy(explanation = ExplState.Pending)
    }

    fun markStreaming(turnId: Long, partial: String) = replace(turnId) { turn ->
        if (turn.explanation is ExplState.Ready) turn
        else turn.copy(explanation = ExplState.Streaming(partial))
    }

    /** Reasoning tokens arrived and the answer text is still empty. */
    fun markThinking(turnId: Long) = replace(turnId) { turn ->
        val state = turn.explanation
        if (state is ExplState.Ready || state is ExplState.Error) turn
        else if (state is ExplState.Streaming && (state.partial.isNotBlank() || state.thinking)) turn
        else turn.copy(explanation = ExplState.Streaming(partial = "", thinking = true))
    }

    /**
     * A background generation finished. Sets unread. A cache hit is never
     * passed here; [appendAfterGrade] already stored it as read.
     * Returns false when the turn is gone or was already Ready.
     */
    fun markGenerated(turnId: Long, text: String): Boolean {
        var changed = false
        synchronized(lock) {
            val index = turns.indexOfFirst { it.turnId == turnId }
            if (index < 0) return false
            val turn = turns[index]
            if (turn.explanation is ExplState.Ready) return false
            val copy = turns.toMutableList()
            copy[index] = turn.copy(
                explanation = ExplState.Ready(text, cached = false),
                unread = true
            )
            turns = copy
            changed = true
        }
        if (changed) {
            snapshot().firstOrNull { it.turnId == turnId }?.let { persist(it) }
            dispatch()
        }
        return changed
    }

    fun markFailed(turnId: Long, message: String, retryable: Boolean) = replace(turnId) { turn ->
        if (turn.explanation is ExplState.Ready) turn
        else turn.copy(explanation = ExplState.Error(message, retryable), unread = false)
    }

    fun replaceFollowUps(turnId: Long, followUps: List<FollowUp>) = replace(turnId) { turn ->
        if (turn.followUps == followUps) turn else turn.copy(followUps = followUps)
    }

    /** A later correction of the learner's letter and ease. Does not speak or advance. */
    fun updateGrade(turnId: Long, transcript: String, ease: Int): Boolean {
        var found = false
        replace(turnId) { turn ->
            found = true
            turn.copy(transcript = transcript, ease = ease)
        }
        if (found) snapshot().firstOrNull { it.turnId == turnId }?.let { persist(it) }
        return found
    }

    /** Drop a finished explanation so a new one can run. */
    fun forceQueued(turnId: Long) = replace(turnId) { turn ->
        turn.copy(explanation = ExplState.Queued, unread = false)
    }

    /** @return index of the new question, or -1 when the turn is gone. */
    fun addFollowUp(turnId: Long, question: String, at: Long): Int {
        var index = -1
        synchronized(lock) {
            val found = turns.indexOfFirst { it.turnId == turnId }
            if (found < 0) return -1
            val turn = turns[found]
            val next = turn.followUps + FollowUp(question, ExplState.Pending, at)
            index = next.lastIndex
            val copy = turns.toMutableList()
            copy[found] = turn.copy(followUps = next)
            turns = copy
        }
        dispatch()
        return index
    }

    fun markFollowStreaming(turnId: Long, index: Int, partial: String) = updateFollow(turnId, index) { follow ->
        follow.copy(state = ExplState.Streaming(partial))
    }

    fun markFollowReady(turnId: Long, index: Int, text: String, at: Long) = updateFollow(turnId, index) { follow ->
        follow.copy(state = ExplState.Ready(text, cached = false), replyAt = at)
    }

    fun markFollowFailed(turnId: Long, index: Int, message: String) = updateFollow(turnId, index) { follow ->
        follow.copy(state = ExplState.Error(message, retryable = true))
    }

    private fun updateFollow(turnId: Long, index: Int, block: (FollowUp) -> FollowUp) {
        replace(turnId) { turn ->
            if (index !in turn.followUps.indices) return@replace turn
            val updated = block(turn.followUps[index])
            if (updated == turn.followUps[index]) return@replace turn
            val copy = turn.followUps.toMutableList()
            copy[index] = updated
            turn.copy(followUps = copy)
        }
    }

    /** @return true when an unread badge was cleared. */
    fun markRead(turnId: Long): Boolean {
        var changed = false
        synchronized(lock) {
            val index = turns.indexOfFirst { it.turnId == turnId }
            if (index < 0) return false
            val turn = turns[index]
            if (!turn.unread) return false
            val copy = turns.toMutableList()
            copy[index] = turn.copy(unread = false)
            turns = copy
            changed = true
        }
        if (changed) dispatch()
        return true
    }

    private fun replace(turnId: Long, block: (ReviewTurn) -> ReviewTurn) {
        var changed = false
        synchronized(lock) {
            val index = turns.indexOfFirst { it.turnId == turnId }
            if (index < 0) return
            val updated = block(turns[index])
            if (updated == turns[index]) return
            val copy = turns.toMutableList()
            copy[index] = updated
            turns = copy
            changed = true
        }
        if (changed) dispatch()
    }

    private fun persist(turn: ReviewTurn) {
        runCatching { ChatHistoryStore.upsert(StoredChat.ofTurn(turn)) }
    }

    private fun dispatch() {
        val snapshot = snapshot()
        for (listener in listeners) {
            try {
                listener(snapshot)
            } catch (_: Exception) {
            }
        }
    }
}
