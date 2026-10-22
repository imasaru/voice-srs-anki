package com.evan.voicesrs

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Ask-the-AI turns that are not attached to a card.
 * The composer writes here when [ChatTarget.composer] is general.
 */
object GeneralChat {
    private const val TAG = "GeneralChat"
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var nextId = 1L
    private var items: List<ChatRow.Ask> = emptyList()
    private var notice: String? = null

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun snapshot(): List<ChatRow.Ask> = synchronized(lock) { items }

    fun notice(): String? = synchronized(lock) { notice }

    fun showNotice(message: String) {
        val text = message.trim()
        synchronized(lock) { notice = text.ifEmpty { null } }
        dispatch()
    }

    fun clearNotice() {
        synchronized(lock) { notice = null }
        dispatch()
    }

    fun clear() {
        synchronized(lock) {
            items = emptyList()
            notice = null
        }
        dispatch()
    }

    fun ask(context: Context, deck: String, question: String) {
        val q = question.trim()
        if (q.isEmpty()) {
            showNotice(ChatSend.visible(ChatSend.Fail.EMPTY))
            return
        }
        val name = deck.trim()
        val id: Long
        synchronized(lock) {
            notice = null
            id = nextId++
            items = items + ChatRow.Ask(id, q, ExplState.Pending, name)
        }
        dispatch()
        val app = context.applicationContext
        Thread({
            var lastPaint = -1L
            try {
                val cfg = Prefs.load(app)
                val text = AiClient().completeChat(
                    baseUrl = cfg.aiBaseUrl,
                    apiKey = cfg.aiApiKey,
                    model = cfg.aiModel,
                    system = PromptBuilder.generalSystem(),
                    messages = PromptBuilder.generalMessages(name, q),
                    cancelled = { false }
                ) { partial ->
                    val now = System.currentTimeMillis()
                    if (ScrollAnchor.paintNow(now, lastPaint, streamingOnly = true)) {
                        val shown = ThinkStrip.strip(partial).trim()
                        if (shown.isNotBlank()) {
                            lastPaint = now
                            mark(id, ExplState.Streaming(shown))
                        }
                    }
                }
                val shown = ThinkStrip.strip(text).trim()
                if (shown.isBlank()) {
                    mark(id, ExplState.Error(ChatSend.visible(ChatSend.Fail.AI), retryable = true))
                } else {
                    mark(id, ExplState.Ready(shown, cached = false))
                }
            } catch (e: Exception) {
                Log.w(TAG, "general ask failed: ${e.javaClass.simpleName}")
                mark(id, ExplState.Error(ChatSend.visible(ChatSend.kindOf(e)), retryable = true))
            }
        }, "general-ask").start()
    }

    private fun mark(id: Long, state: ExplState) {
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return
            val copy = items.toMutableList()
            copy[index] = copy[index].copy(state = state)
            items = copy
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
