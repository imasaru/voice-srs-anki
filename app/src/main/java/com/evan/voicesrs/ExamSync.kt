package com.evan.voicesrs

import android.content.Context
import android.util.Log
import java.time.LocalDate

/**
 * Applies [ExamApply] through stock AnkiConnect. Clones the deck's preset
 * once, assigns it only to this deck, then writes `desiredRetention`.
 */
object ExamSync {
    private const val TAG = "ExamSync"

    fun run(ctx: Context, anki: AnkiConnect, deck: String, today: LocalDate = LocalDate.now()): String {
        val state = try {
            Prefs.loadExam(ctx, deck)
        } catch (_: Exception) {
            return ""
        }
        val exam = state.date ?: return ""
        val days = ExamPlan.daysLeft(today, exam)
        if (days < 0) {
            restoreIfNeeded(ctx, anki, deck, state, today)
            return ""
        }
        val skip = state.lastApplied == today
        var configId: Long? = null
        val coveragePct = if (skip) {
            state.coverage ?: 0
        } else {
            val cov = try {
                coverage(anki, deck, days)
            } catch (e: Exception) {
                Log.w(TAG, "coverage failed: ${e.javaClass.simpleName}")
                ExamPlan.Coverage(0, state.coverage ?: 0)
            }
            configId = try {
                val id = anki.getDeckConfig(deck).optLong("id", 0L)
                if (id == 0L) null else id
            } catch (e: Exception) {
                Log.w(TAG, "getDeckConfig failed: ${e.javaClass.simpleName}")
                null
            }
            cov.percent
        }
        val plan = ExamApply.plan(
            ExamApply.Input(
                deck = deck,
                today = today,
                examDate = exam,
                targetOverride = state.target,
                cloneId = state.cloneId,
                lastApplied = state.lastApplied,
                configId = configId,
                coveragePercent = coveragePct
            )
        )
        if (!plan.skipWrites) {
            try {
                execute(ctx, anki, deck, plan, state, today, coveragePct)
            } catch (e: Exception) {
                Log.w(TAG, "apply failed: ${e.javaClass.simpleName}")
            }
        } else if (!skip && coveragePct != state.coverage) {
            try {
                Prefs.saveExam(ctx, deck, state.copy(coverage = coveragePct))
            } catch (_: Exception) {
            }
        }
        return plan.spoken
    }

    fun coverage(anki: AnkiConnect, deck: String, daysLeft: Int): ExamPlan.Coverage {
        val total = anki.findCardIds(ExamApply.allQuery(deck)).size
        val covered = anki.findCardIds(ExamApply.coveredQuery(deck, daysLeft)).size
        return ExamPlan.Coverage(total, covered)
    }

    private fun execute(
        ctx: Context,
        anki: AnkiConnect,
        deck: String,
        plan: ExamApply.Plan,
        state: ExamStore.State,
        today: LocalDate,
        coveragePct: Int
    ) {
        var cloneId = state.cloneId
        var origCfg = state.origCfg
        if (plan.clone) {
            val from = plan.cloneFrom ?: return
            val name = plan.cloneName ?: ExamApply.cloneName(deck)
            val newId = anki.cloneDeckConfigId(name, from) ?: return
            cloneId = newId
            origCfg = from
            try {
                Prefs.saveExam(ctx, deck, state.copy(cloneId = newId, origCfg = from))
            } catch (_: Exception) {
            }
            anki.setDeckConfigId(listOf(deck), newId)
        } else if (plan.assign) {
            val id = plan.assignId ?: cloneId ?: return
            anki.setDeckConfigId(listOf(deck), id)
        }
        if (plan.save && plan.retention != null) {
            val cfg = anki.getDeckConfig(deck)
            val updated = DeckConfigRequest.withDesiredRetention(cfg, plan.retention)
            anki.saveDeckConfig(updated)
        }
        if (plan.sweep && plan.sweepRange != null) {
            val ids = anki.findCardIds(ExamApply.sweepQuery(deck, plan.daysLeft))
            if (ids.isNotEmpty()) anki.setDueDate(ids, plan.sweepRange)
        }
        try {
            Prefs.saveExam(
                ctx,
                deck,
                state.copy(
                    cloneId = cloneId,
                    origCfg = origCfg,
                    lastApplied = today,
                    coverage = coveragePct
                )
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Reassign the deck's original preset and drop the saved config prefs when
     * [ExamRestore.needsRestore] is true (exam date in the past). On a failed
     * reassignment the saved prefs are kept (see [ExamRestore.decide]) so the next
     * run retries, and the failure is surfaced via the existing Log path.
     * Returns true when a restore was attempted.
     */
    private fun restoreIfNeeded(
        ctx: Context,
        anki: AnkiConnect,
        deck: String,
        state: ExamStore.State,
        today: LocalDate
    ) {
        if (!ExamRestore.needsRestore(state, today)) return
        val orig = state.origCfg ?: return
        val decision = try {
            ExamRestore.decide(anki.setDeckConfigId(listOf(deck), orig))
        } catch (e: Exception) {
            Log.w(TAG, "restore setDeckConfigId failed: ${e.javaClass.simpleName}")
            ExamRestore.decide(false)
        }
        if (decision.reportError) {
            Log.w(TAG, "exam restore did not complete for $deck; keeping saved config to retry")
        }
        try {
            Prefs.saveExam(ctx, deck, ExamRestore.newState(state, decision))
        } catch (_: Exception) {
        }
    }
}
