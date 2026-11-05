package com.evan.voicesrs

import android.content.Context
import android.util.Log

/**
 * Applies a confirmed past-card grade. Never pauses, speaks, or advances
 * the live card. setDueDate is not used.
 */
object GradeFix {
    private const val TAG = "GradeFix"

    fun apply(context: Context, turn: ReviewTurn, preview: PastGrade.Preview) {
        val app = context.applicationContext
        val oldLetter = TapAnswer.parseSpoken(
            turn.transcript,
            QuestionCard.parse(
                BubbleModel.fullQuestion(turn.frontText).ifBlank { turn.frontText }
            ).options.map { it.letter }.toSet()
        )
        val hasExplanation = turn.explanation !is ExplState.None
        val commit = if (preview.letter.isBlank()) {
            PastGrade.commitEase(turn.transcript, preview.ease)
        } else {
            PastGrade.commit(oldLetter, preview.letter, preview.ease, hasExplanation)
        }
        ReviewLog.updateGrade(turn.turnId, commit.transcript, commit.ease)
        Thread({
            try {
                val existing = ThreadStore.find(
                    ThreadStore.load(app.filesDir),
                    turn.cardId,
                    turn.noteId,
                    turn.contentHash
                )
                val messages = existing?.messages ?: ThreadStore.messagesOf(
                    ReviewLog.snapshot().firstOrNull { it.turnId == turn.turnId }?.followUps.orEmpty()
                )
                ThreadStore.upsert(
                    app.filesDir,
                    StoredThread(
                        cardId = turn.cardId,
                        noteId = turn.noteId,
                        contentHash = turn.contentHash,
                        messages = messages,
                        ease = commit.ease,
                        transcript = commit.transcript
                    )
                )
            } catch (e: Exception) {
                DLog.w(TAG, "past grade thread save failed: ${e.javaClass.simpleName}")
            }
            try {
                val store = LocalQueueStore(app.filesDir)
                val now = System.currentTimeMillis()
                val result = PastGrade.replacePending(store.readGrades(), turn.cardId, commit.ease, now)
                if (result.replaced) {
                    store.writeGrades(result.grades)
                } else if (!result.usesSetDueDate) {
                    val cfg = Prefs.load(app)
                    AnkiConnect(cfg.url, cfg.apiKey).answerCards(
                        listOf(PendingGrade(turn.cardId, commit.ease, now))
                    )
                }
            } catch (e: Exception) {
                DLog.w(TAG, "past grade sync failed: ${e.javaClass.simpleName}")
            }
        }, "past-grade").start()
        if (commit.regenerate) {
            ReviewLog.forceQueued(turn.turnId)
            ExplainQueue.retry(app, turn.turnId)
        }
    }
}
