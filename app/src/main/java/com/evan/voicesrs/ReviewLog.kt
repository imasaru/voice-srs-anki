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
    val unread: Boolean = false
)

sealed interface ExplState {
    data object None : ExplState
    data object Queued : ExplState
    data object Pending : ExplState
    data class Streaming(val partial: String) : ExplState
    data class Ready(val text: String, val cached: Boolean) : ExplState
    data class Error(val message: String, val retryable: Boolean) : ExplState
}

data class FollowUp(val q: String, val state: ExplState)

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
        aiEnabled: Boolean
    ): ReviewTurn? {
        if (ease == null) return null
        val explanation = when {
            !aiEnabled -> ExplState.None
            cachedText != null -> ExplState.Ready(cachedText, cached = true)
            else -> ExplState.Queued
        }
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
                unread = false
            )
            turns = turns + created
            created
        }
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
        if (changed) dispatch()
        return changed
    }

    fun markFailed(turnId: Long, message: String, retryable: Boolean) = replace(turnId) { turn ->
        if (turn.explanation is ExplState.Ready) turn
        else turn.copy(explanation = ExplState.Error(message, retryable), unread = false)
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
