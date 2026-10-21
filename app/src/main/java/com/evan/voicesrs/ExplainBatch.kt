package com.evan.voicesrs

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Enqueue or cancel the overnight explanation pass. KEEP when it is already scheduled. */
object ExplainBatchSchedule {
    const val WORK_NAME = "ai-explain-batch"
    private const val TAG = ExplainLog.TAG

    fun sync(context: Context, cfg: AnkiConfig) {
        val wm = try {
            WorkManager.getInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "work manager unavailable: ${e.javaClass.simpleName}")
            return
        }
        if (cfg.aiEnabled && cfg.aiBatch) {
            val request = PeriodicWorkRequestBuilder<ExplainBatchWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresDeviceIdle(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }
}

/**
 * One pass over the deck. Each finished explanation is committed before the
 * next card, so a stop resumes on the following night. Skips the whole run
 * while a drill is in progress. Never speaks.
 */
class ExplainBatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (isStopped || DrillService.running) return Result.success()
        val cfg = try {
            Prefs.load(applicationContext)
        } catch (_: Exception) {
            return Result.success()
        }
        if (!cfg.aiEnabled || !cfg.aiBatch) return Result.success()
        val anki = AnkiConnect(cfg.url, cfg.apiKey)
        val ids = try {
            anki.deckCardIds(cfg.deck)
        } catch (e: Exception) {
            Log.w(ExplainLog.TAG, "batch deck list failed: ${e.javaClass.simpleName}")
            return Result.retry()
        }
        val client = AiClient()
        var consecutive = 0
        for (chunk in ids.chunked(CHUNK)) {
            if (isStopped || DrillService.running) return Result.success()
            val cards = try {
                anki.cardsInfo(chunk)
            } catch (e: Exception) {
                Log.w(ExplainLog.TAG, "batch cardsInfo failed: ${e.javaClass.simpleName}")
                return Result.retry()
            }
            for (card in cards) {
                if (isStopped || DrillService.running) return Result.success()
                val noteKey = card.noteId ?: card.cardId
                val hash = ContentHash.of(PromptBuilder.PROMPT_VERSION, card.front, card.back)
                val hit = try {
                    AiCache.has(applicationContext, noteKey, hash, PromptBuilder.PROMPT_VERSION)
                } catch (_: Exception) {
                    false
                }
                if (hit) {
                    consecutive = 0
                    continue
                }
                try {
                    val text = client.complete(
                        baseUrl = cfg.aiBaseUrl,
                        apiKey = cfg.aiApiKey,
                        model = cfg.aiModel,
                        system = PromptBuilder.SYSTEM,
                        user = PromptBuilder.userMessage(card.front, card.back),
                        cancelled = { isStopped || DrillService.running }
                    )
                    AiCache.put(
                        applicationContext,
                        ExplanationRow(
                            noteId = noteKey,
                            contentHash = hash,
                            model = cfg.aiModel,
                            promptVersion = PromptBuilder.PROMPT_VERSION,
                            text = text,
                            createdAt = System.currentTimeMillis(),
                            lastUsedAt = System.currentTimeMillis(),
                            source = AiCache.SOURCE_BATCH
                        )
                    )
                    consecutive = 0
                } catch (_: AiCancelled) {
                    return Result.success()
                } catch (e: Exception) {
                    val kind = AiClient.classify(e)
                    Log.w(ExplainLog.TAG, "batch card failed: ${e.javaClass.simpleName}")
                    if (!ExplainBackoff.retries(kind)) continue
                    consecutive += 1
                    if (consecutive >= 3) return Result.retry()
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val CHUNK = 50
    }
}
