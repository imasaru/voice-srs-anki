package com.evan.voicesrs

import android.content.Context
import android.util.Log
import org.json.JSONArray
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
        startAsk(context, deck, question, injectReport = false)
    }

    /** Runs the three study tools for today and passes the results in once as context. */
    fun todayReport(context: Context, deck: String) {
        startAsk(context, deck, context.getString(R.string.today_report_prompt), injectReport = true)
    }

    private fun startAsk(context: Context, deck: String, question: String, injectReport: Boolean) {
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
            items = items + ChatRow.Ask(id, q, ExplState.Pending, name, at = System.currentTimeMillis())
        }
        val issuedAt = System.currentTimeMillis()
        dispatch()
        val app = context.applicationContext
        Thread({
            var lastPaint = -1L
            try {
                val cfg = Prefs.load(app)
                val client = AiClient()
                val tools: JSONArray?
                val onTool: ((String, String) -> String)?
                val contextBlob: String
                if (injectReport) {
                    tools = null
                    onTool = null
                    contextBlob = try {
                        val anki = AnkiConnect(cfg.url, cfg.apiKey)
                        StudyToolRunner(anki, cfg.deck, pending = { LocalQueueStore(app.filesDir).readGrades() }).todayBundle(cfg.deck)
                    } catch (e: Exception) {
                        Log.w(TAG, "today report tools failed: ${e.javaClass.simpleName}")
                        """{"error":"${e.javaClass.simpleName}"}"""
                    }
                } else {
                    contextBlob = ""
                    tools = StudyTools.schemas()
                    onTool = { toolName, args ->
                        val anki = AnkiConnect(cfg.url, cfg.apiKey)
                        StudyToolRunner(anki, cfg.deck, pending = { LocalQueueStore(app.filesDir).readGrades() }).invoke(toolName, args)
                    }
                }
                val paint: (String) -> Unit = { partial ->
                    val now = System.currentTimeMillis()
                    if (ScrollAnchor.paintNow(now, lastPaint, streamingOnly = true)) {
                        val shown = ThinkStrip.strip(partial).trim()
                        if (shown.isNotBlank()) {
                            lastPaint = now
                            mark(id, ExplState.Streaming(
                                if (injectReport) ReportHeader.prepend(issuedAt, shown) else shown
                            ))
                        }
                    }
                }
                val text = try {
                    client.completeChat(
                        baseUrl = cfg.aiBaseUrl,
                        apiKey = cfg.aiApiKey,
                        model = cfg.aiModel,
                        system = PromptBuilder.generalSystem(!injectReport),
                        messages = PromptBuilder.generalMessages(name, q, contextBlob),
                        cancelled = { false },
                        tools = tools,
                        onTool = onTool,
                        onDelta = paint
                    )
                } catch (e: ToolsRejected) {
                    Log.w(TAG, "study tools rejected")
                    showNotice(app.getString(R.string.today_report_fallback))
                    client.completeChat(
                        baseUrl = cfg.aiBaseUrl,
                        apiKey = cfg.aiApiKey,
                        model = cfg.aiModel,
                        system = PromptBuilder.generalSystem(false),
                        messages = PromptBuilder.generalMessages(name, q),
                        cancelled = { false },
                        onDelta = paint
                    )
                }
                if (!injectReport &&
                    StudyTools.needsFallbackChip(true, client.lastSawToolCalls, false) &&
                    StudyTools.looksLikeStudyAsk(q)
                ) {
                    showNotice(app.getString(R.string.today_report_fallback))
                }
                val shown = ThinkStrip.strip(text).trim()
                if (shown.isBlank()) {
                    mark(id, ExplState.Error(ChatSend.visible(ChatSend.Fail.AI), retryable = true))
                } else {
                    mark(id, ExplState.Ready(
                        if (injectReport) ReportHeader.prepend(issuedAt, shown) else shown,
                        cached = false
                    ))
                }
            } catch (e: Exception) {
                Log.w(TAG, "general ask failed: ${e.javaClass.simpleName}")
                mark(id, ExplState.Error(ChatSend.visible(ChatSend.kindOf(e)), retryable = true))
            }
        }, "general-ask").start()
    }

    private fun mark(id: Long, state: ExplState) {
        var stored: ChatRow.Ask? = null
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return
            val copy = items.toMutableList()
            copy[index] = copy[index].copy(state = state)
            items = copy
            stored = copy[index]
        }
        stored?.let { row ->
            StoredChat.ofAsk(row)?.let { runCatching { ChatHistoryStore.upsert(it) } }
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
