package com.evan.voicesrs

import android.content.Context
import android.os.Process
import android.util.Log

/**
 * Process-wide explanation worker. One background thread.
 * It never calls TTS and never runs on the drill or microphone thread.
 * Stop drops LOOKAHEAD. ANSWERED jobs finish and land in [AiCache].
 */
object ExplainQueue {
    private const val TAG = ExplainLog.TAG
    private val logic = ExplainQueueLogic({ System.currentTimeMillis() })
    private val wake = Object()
    private var thread: Thread? = null
    @Volatile private var app: Context? = null

    fun ensureStarted(context: Context) {
        app = context.applicationContext
        synchronized(wake) {
            if (thread?.isAlive == true) return
            val worker = Thread(::loop, "explain-queue")
            thread = worker
            worker.start()
        }
    }

    fun lookaheadOpen(): Boolean = logic.lookaheadOpen()

    fun enqueueAnswered(
        context: Context,
        key: ExplainKey,
        cardId: Long,
        front: String,
        back: String,
        turnId: Long
    ) {
        ensureStarted(context)
        val effect = logic.enqueue(key, cardId, front, back, turnId, ExplainKind.ANSWERED)
        if (effect == EnqueueEffect.ADDED || effect == EnqueueEffect.RAISED) {
            ExplainLog.enqueue(ExplainKind.ANSWERED, key.noteId)
        }
        wake()
    }

    fun enqueueLookahead(
        context: Context,
        key: ExplainKey,
        cardId: Long,
        front: String,
        back: String
    ) {
        ensureStarted(context)
        val effect = logic.enqueue(key, cardId, front, back, null, ExplainKind.LOOKAHEAD)
        if (effect == EnqueueEffect.ADDED || effect == EnqueueEffect.RAISED) {
            ExplainLog.enqueue(ExplainKind.LOOKAHEAD, key.noteId)
        }
        wake()
    }

    fun retry(context: Context, turnId: Long) {
        val turn = ReviewLog.snapshot().firstOrNull { it.turnId == turnId } ?: return
        ReviewLog.markQueued(turnId)
        enqueueAnswered(
            context,
            ExplainKey(turn.noteId ?: turn.cardId, turn.contentHash),
            turn.cardId,
            turn.frontText,
            turn.backText,
            turnId
        )
    }

    /** Drill stopped. Queued lookahead is dropped. A running lookahead is cancelled. */
    fun onDrillStopped() {
        logic.dropLookahead()
        wake()
    }

    private fun wake() {
        synchronized(wake) { wake.notifyAll() }
    }

    private fun loop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Exception) {
        }
        val client = AiClient()
        while (!Thread.currentThread().isInterrupted) {
            try {
                val job = logic.poll()
                if (job == null) {
                    val wait = logic.delayUntilReady()
                    synchronized(wake) { wake.wait(wait) }
                    continue
                }
                if (job.cancelRequested) {
                    logic.abandon(job)
                    continue
                }
                runJob(client, job)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Log.w(TAG, "explain worker error: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun runJob(client: AiClient, job: ExplainWork) {
        val context = app ?: run {
            logic.abandon(job)
            return
        }
        val turnId = job.turnId
        if (turnId != null) ReviewLog.markPending(turnId)
        if (job.kind == ExplainKind.LOOKAHEAD && !logic.lookaheadOpen()) {
            logic.abandon(job)
            return
        }
        val cfg = try {
            Prefs.load(context)
        } catch (e: Exception) {
            Log.w(TAG, "explain prefs unavailable: ${e.javaClass.simpleName}")
            logic.abandon(job)
            return
        }
        var lastUi = 0L
        try {
            val raw = client.complete(
                baseUrl = cfg.aiBaseUrl,
                apiKey = cfg.aiApiKey,
                model = cfg.aiModel,
                system = PromptBuilder.SYSTEM,
                user = PromptBuilder.userMessage(job.front, job.back),
                cancelled = { job.cancelRequested },
                onThinking = {
                    val id = job.turnId ?: return@complete
                    ReviewLog.markThinking(id)
                }
            ) { partial ->
                val id = job.turnId ?: return@complete
                val shown = BubbleModel.visibleExplanation(partial, job.front, job.back)
                if (shown.isBlank()) return@complete
                val now = System.currentTimeMillis()
                if (now - lastUi < 250L) return@complete
                lastUi = now
                ReviewLog.markStreaming(id, shown)
            }
            val text = BubbleModel.visibleExplanation(raw, job.front, job.back)
            if (text.isBlank()) throw AiException(AiMessages.NO_TEXT, retryable = false)
            AiCache.put(
                context,
                ExplanationRow(
                    noteId = job.key.noteId,
                    contentHash = job.key.contentHash,
                    model = cfg.aiModel,
                    promptVersion = PromptBuilder.PROMPT_VERSION,
                    text = text,
                    createdAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis(),
                    source = AiCache.SOURCE_LIVE
                )
            )
            val finishedTurn = job.turnId
            if (finishedTurn != null && ReviewLog.markGenerated(finishedTurn, text)) {
                ExplainLog.ready(finishedTurn, cached = false)
            }
            logic.succeed(job)
        } catch (e: AiCancelled) {
            logic.abandon(job)
            val id = job.turnId
            if (id != null) ReviewLog.markQueued(id)
        } catch (e: Exception) {
            val outcome = logic.fail(job, AiClient.classify(e))
            val id = job.turnId
            if (id != null) {
                if (outcome.retry) ReviewLog.markQueued(id)
                else ReviewLog.markFailed(id, AiClient.publicMessage(e), retryable = true)
            }
        }
    }
}
