package com.evan.voicesrs

import android.content.Context
import android.util.Log

/**
 * One follow-up turn against the configured model (Aperture when that is the
 * saved base URL). The question, the learner's answer, and the explanation
 * are the context. Replies are written back onto the turn and into [ThreadStore].
 */
object ThreadAsk {
    private const val TAG = "ThreadAsk"

    fun send(context: Context, turnId: Long) {
        val turn = ReviewLog.snapshot().firstOrNull { it.turnId == turnId } ?: return
        val stored = try {
            ThreadStore.toFollowUps(
                ThreadStore.find(
                    ThreadStore.load(context.filesDir),
                    turn.cardId,
                    turn.noteId,
                    turn.contentHash
                )?.messages.orEmpty()
            )
        } catch (_: Exception) {
            emptyList()
        }
        if (stored.isNotEmpty() && turn.followUps.isEmpty()) {
            ReviewLog.replaceFollowUps(turnId, stored)
        }
    }

    fun ask(context: Context, turnId: Long, question: String, byVoice: Boolean = false) {
        val q = question.trim()
        if (q.isEmpty()) return
        if (GeneralChat.routeReport(context, q)) return
        val app = context.applicationContext
        val at = System.currentTimeMillis()
        val index = ReviewLog.addFollowUp(turnId, q, at)
        if (index < 0) return
        persist(app, turnId)
        Thread({
            var lastPaint = -1L
            try {
                val turn = ReviewLog.snapshot().firstOrNull { it.turnId == turnId } ?: return@Thread
                val cfg = Prefs.load(app)
                val prior = turn.followUps.filterIndexed { i, _ -> i != index }
                val explanation = when (val state = turn.explanation) {
                    is ExplState.Ready -> BubbleModel.visibleExplanation(state.text, turn.frontText, turn.backText)
                    is ExplState.Streaming -> BubbleModel.visibleExplanation(state.partial, turn.frontText, turn.backText)
                    else -> ""
                }
                val label = turn.questionLabel.ifBlank {
                    QuestionLabel.of(turn.frontText, 1).label
                }
                val messages = PromptBuilder.followUpMessages(
                    turn.frontText,
                    turn.backText,
                    turn.transcript,
                    explanation,
                    prior,
                    q,
                    label
                )
                val text = AiClient().completeChat(
                    baseUrl = cfg.aiBaseUrl,
                    apiKey = cfg.aiApiKey,
                    model = cfg.aiModel,
                    system = PromptBuilder.followUpSystem(),
                    messages = messages,
                    cancelled = { false }
                ) { partial ->
                    val now = System.currentTimeMillis()
                    if (ScrollAnchor.paintNow(now, lastPaint, streamingOnly = true)) {
                        val shown = BubbleModel.visibleExplanation(partial, turn.frontText, turn.backText)
                        if (shown.isNotBlank()) {
                            lastPaint = now
                            ReviewLog.markFollowStreaming(turnId, index, shown)
                        }
                    }
                }
                val shown = BubbleModel.visibleExplanation(text, turn.frontText, turn.backText)
                if (shown.isBlank()) {
                    ReviewLog.markFollowFailed(turnId, index, AiMessages.NO_TEXT)
                } else {
                    ReviewLog.markFollowReady(turnId, index, shown, System.currentTimeMillis())
                    DrillService.offerChatReply(shown, byVoice)
                }
            } catch (e: Exception) {
                Log.w(TAG, "thread reply failed: ${e.javaClass.simpleName}")
                ReviewLog.markFollowFailed(turnId, index, ChatSend.askAgain(ChatSend.kindOf(e)))
            }
            persist(app, turnId)
        }, "thread-ask").start()
    }

    private fun persist(context: Context, turnId: Long) {
        val turn = ReviewLog.snapshot().firstOrNull { it.turnId == turnId } ?: return
        try {
            ThreadStore.upsert(
                context.filesDir,
                StoredThread(
                    cardId = turn.cardId,
                    noteId = turn.noteId,
                    contentHash = turn.contentHash,
                    messages = ThreadStore.messagesOf(turn.followUps)
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "thread save failed: ${e.javaClass.simpleName}")
        }
    }
}
