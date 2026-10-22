package com.evan.voicesrs

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Follow-ups for the card currently on screen, before it is a graded turn.
 * The main composer writes here. Older cards stay on [ThreadAsk].
 */
object LiveThread {
    private const val TAG = "LiveThread"
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var current: ChatTarget.Screen? = null
    private var noteId: Long? = null
    private var front: String = ""
    private var back: String = ""
    private var transcript: String = ""
    private var followUps: List<FollowUp> = emptyList()

    data class Card(
        val screen: ChatTarget.Screen,
        val noteId: Long?,
        val front: String,
        val back: String,
        val transcript: String,
        val followUps: List<FollowUp>
    )

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun screen(): ChatTarget.Screen? = synchronized(lock) { current }

    fun snapshot(): Card? = synchronized(lock) {
        val screen = current ?: return null
        Card(screen, noteId, front, back, transcript, followUps)
    }

    /** A new question. A different card drops the previous card's unmerged asks. */
    fun open(screen: ChatTarget.Screen, note: Long?, frontText: String) {
        synchronized(lock) {
            val same = current?.cardId == screen.cardId && current?.ordinal == screen.ordinal
            current = screen
            noteId = note
            front = frontText
            if (!same) {
                back = ""
                transcript = ""
                followUps = emptyList()
            }
        }
        dispatch()
    }

    /** The answer is on screen. Follow-ups already asked stay on this card. */
    fun reveal(backText: String, said: String) {
        synchronized(lock) {
            val screen = current ?: return
            current = screen.copy(revealed = true)
            back = backText
            if (said.isNotBlank()) transcript = said
        }
        dispatch()
    }

    fun clear() {
        synchronized(lock) {
            current = null
            noteId = null
            front = ""
            back = ""
            transcript = ""
            followUps = emptyList()
        }
        dispatch()
    }

    /**
     * Move this card's asks onto the graded turn. Empty when the id does not match,
     * so a late reply cannot land on the next card.
     */
    fun take(cardId: Long): List<FollowUp> = synchronized(lock) {
        val screen = current ?: return emptyList()
        if (screen.cardId != cardId) return emptyList()
        val out = followUps
        followUps = emptyList()
        out
    }

    fun ask(context: Context, question: String, byVoice: Boolean) {
        val q = question.trim()
        if (q.isEmpty()) return
        val shot = synchronized(lock) {
            val screen = current ?: return
            val at = System.currentTimeMillis()
            val next = followUps + FollowUp(q, ExplState.Pending, at)
            val index = next.lastIndex
            followUps = next
            Shot(screen, front, back, transcript, next.filterIndexed { i, _ -> i != index }, index, byVoice)
        }
        dispatch()
        val app = context.applicationContext
        Thread({
            try {
                val cfg = Prefs.load(app)
                val messages: List<PromptBuilder.ChatMsg>
                val system: String
                if (shot.screen.revealed) {
                    messages = PromptBuilder.followUpMessages(
                        shot.front,
                        shot.back,
                        shot.transcript,
                        "",
                        shot.prior,
                        q
                    )
                    system = PromptBuilder.followUpSystem()
                } else {
                    messages = PromptBuilder.hintMessages(shot.front, shot.prior, q)
                    system = PromptBuilder.hintSystem()
                }
                val text = AiClient().completeChat(
                    baseUrl = cfg.aiBaseUrl,
                    apiKey = cfg.aiApiKey,
                    model = cfg.aiModel,
                    system = system,
                    messages = messages,
                    cancelled = { false }
                ) { partial ->
                    val shown = BubbleModel.visibleExplanation(partial, shot.front, shot.back)
                    if (shown.isNotBlank()) mark(shot.screen, shot.index, ExplState.Streaming(shown))
                }
                val shown = BubbleModel.visibleExplanation(text, shot.front, shot.back)
                if (shown.isBlank()) {
                    mark(shot.screen, shot.index, ExplState.Error(AiMessages.NO_TEXT, retryable = true))
                } else {
                    mark(shot.screen, shot.index, ExplState.Ready(shown, cached = false), System.currentTimeMillis())
                    DrillService.offerChatReply(shown, byVoice)
                }
            } catch (e: Exception) {
                Log.w(TAG, "live reply failed: ${e.javaClass.simpleName}")
                mark(shot.screen, shot.index, ExplState.Error(AiClient.publicMessage(e), retryable = true))
            }
        }, "live-ask").start()
    }

    private data class Shot(
        val screen: ChatTarget.Screen,
        val front: String,
        val back: String,
        val transcript: String,
        val prior: List<FollowUp>,
        val index: Int,
        val byVoice: Boolean
    )

    private fun mark(screen: ChatTarget.Screen, index: Int, state: ExplState, replyAt: Long = 0L) {
        synchronized(lock) {
            val now = current ?: return
            if (now.cardId != screen.cardId || now.ordinal != screen.ordinal) return
            if (index !in followUps.indices) return
            val copy = followUps.toMutableList()
            val follow = copy[index]
            copy[index] = follow.copy(state = state, replyAt = if (replyAt > 0L) replyAt else follow.replyAt)
            followUps = copy
        }
        dispatch()
    }

    private fun dispatch() {
        for (listener in listeners) {
            try {
                listener()
            } catch (_: Exception) {
            }
        }
    }
}
