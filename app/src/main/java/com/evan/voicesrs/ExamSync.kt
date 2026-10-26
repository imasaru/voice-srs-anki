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

    /**
     * Outcome of an exam apply. [error] is set only when the apply could not
     * complete, so the caller can surface a user-visible failure.
     */
    data class Result(
        val spoken: String,
        val error: String? = null
    )

    fun run(ctx: Context, anki: AnkiConnect, deck: String, today: LocalDate = LocalDate.now()): Result {
        val state = try {
            Prefs.loadExam(ctx, deck)
        } catch (_: Exception) {
            return Result(spoken = "")
        }
        val exam = state.date ?: return Result(spoken = "")
        val days = ExamPlan.daysLeft(today, exam)
        if (days < 0) return Result(spoken = "")
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
                return Result(spoken = plan.spoken)
            } catch (e: Exception) {
                return Result(spoken = plan.spoken, error = ExamError.format(e))
            }
        } else if (!skip && coveragePct != state.coverage) {
            try {
                Prefs.saveExam(ctx, deck, state.copy(coverage = coveragePct))
            } catch (_: Exception) {
            }
        }
        return Result(spoken = plan.spoken)
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
        if (plan.clone) {
            val from = plan.cloneFrom ?: return
            val name = plan.cloneName ?: ExamApply.cloneName(deck)
            val newId = anki.cloneDeckConfigId(name, from) ?: return
            cloneId = newId
            try {
                Prefs.saveExam(ctx, deck, state.copy(cloneId = newId))
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
                state.copy(cloneId = cloneId, lastApplied = today, coverage = coveragePct)
            )
        } catch (_: Exception) {
        }
    }
}
