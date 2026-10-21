package com.evan.voicesrs

import java.util.concurrent.PriorityBlockingQueue

/** ANSWERED outranks LOOKAHEAD. Follow-ups are a later phase. */
enum class ExplainKind { ANSWERED, LOOKAHEAD }

enum class ExplainFail { IO, SERVER, TIMEOUT, CLIENT, CANCEL, OTHER }

data class ExplainKey(val noteId: Long, val contentHash: String)

/**
 * One queued or running explanation. [kind] and [turnId] are mutable so an
 * ANSWERED request can raise an in-queue or in-flight lookahead in place.
 * The queue is re-heaped after an in-queue raise; the worker holds the same
 * instance for an in-flight raise and sees the new fields.
 */
class ExplainWork(
    val key: ExplainKey,
    val cardId: Long,
    val front: String,
    val back: String,
    @Volatile var turnId: Long?,
    @Volatile var kind: ExplainKind,
    val seq: Long,
    val attempt: Int,
    val notBefore: Long
) {
    @Volatile var cancelRequested: Boolean = false
}

enum class EnqueueEffect { ADDED, RAISED, DUPLICATE, DROPPED }

data class FailOutcome(val retry: Boolean, val delayMs: Long, val breakerOpen: Boolean)

object ExplainBackoff {
    /** Automatic retries after the first attempt. Attempt 2 is the last try. */
    const val MAX_RETRIES = 2
    const val BREAKER_AFTER = 3
    const val BREAKER_PAUSE_MS = 5L * 60L * 1000L
    private const val JITTER_MS = 1_000L

    /** Retry index 0 waits 5 s, retry index 1 waits 20 s, plus up to 1 s of jitter. */
    fun delayMs(attempt: Int, unit: Double): Long {
        val base = if (attempt <= 0) 5_000L else 20_000L
        val jitter = (unit.coerceIn(0.0, 0.999999) * JITTER_MS).toLong()
        return base + jitter
    }

    fun retries(fail: ExplainFail): Boolean {
        return fail == ExplainFail.IO || fail == ExplainFail.SERVER || fail == ExplainFail.TIMEOUT
    }
}

/**
 * Priority, dedup, retry, and circuit breaker for [ExplainQueue].
 * [clock] and [randomUnit] are injected so tests do not sleep.
 * The backing store is a [PriorityBlockingQueue]: ANSWERED (0) before
 * LOOKAHEAD (2), then FIFO by [ExplainWork.seq].
 */
class ExplainQueueLogic(
    private val clock: () -> Long,
    private val randomUnit: () -> Double = { 0.0 }
) {
    private val lock = Any()
    private val queue = PriorityBlockingQueue<ExplainWork>(
        16,
        compareBy<ExplainWork>({ rank(it.kind) }, { it.seq })
    )
    private var seqGen = 0L
    private var inflight: ExplainWork? = null
    private var failures = 0
    private var pausedUntil = 0L

    fun lookaheadOpen(now: Long = clock()): Boolean = synchronized(lock) { now >= pausedUntil }

    fun enqueue(
        key: ExplainKey,
        cardId: Long,
        front: String,
        back: String,
        turnId: Long?,
        kind: ExplainKind
    ): EnqueueEffect = synchronized(lock) {
        if (kind == ExplainKind.LOOKAHEAD && clock() < pausedUntil) return EnqueueEffect.DROPPED
        val flying = inflight
        if (flying != null && flying.key == key) {
            if (kind == ExplainKind.ANSWERED && flying.kind == ExplainKind.LOOKAHEAD) {
                flying.kind = ExplainKind.ANSWERED
                if (turnId != null) flying.turnId = turnId
                return EnqueueEffect.RAISED
            }
            if (turnId != null && flying.turnId == null) flying.turnId = turnId
            return EnqueueEffect.DUPLICATE
        }
        val held = drain()
        val existing = held.indexOfFirst { it.key == key }
        if (existing >= 0) {
            val slot = held[existing]
            if (kind == ExplainKind.ANSWERED && slot.kind == ExplainKind.LOOKAHEAD) {
                slot.kind = ExplainKind.ANSWERED
                if (turnId != null) slot.turnId = turnId
                seqGen += 1
                val raised = ExplainWork(
                    slot.key, cardId, front, back, slot.turnId, ExplainKind.ANSWERED,
                    seqGen, slot.attempt, slot.notBefore
                )
                held[existing] = raised
                offerAll(held)
                return EnqueueEffect.RAISED
            }
            offerAll(held)
            return EnqueueEffect.DUPLICATE
        }
        seqGen += 1
        held += ExplainWork(key, cardId, front, back, turnId, kind, seqGen, 0, 0L)
        offerAll(held)
        EnqueueEffect.ADDED
    }

    /**
     * Highest-priority job whose [ExplainWork.notBefore] has arrived.
     * A delayed higher-priority job stays queued so a ready lookahead can run.
     */
    fun poll(now: Long = clock()): ExplainWork? = synchronized(lock) {
        if (inflight != null) return null
        val delayed = ArrayList<ExplainWork>()
        var chosen: ExplainWork? = null
        while (chosen == null) {
            val job = queue.poll() ?: break
            if (job.notBefore <= now) chosen = job else delayed += job
        }
        offerAll(delayed)
        inflight = chosen
        chosen
    }

    /**
     * Drop queued lookaheads. An in-flight lookahead with no answered turn is
     * flagged [ExplainWork.cancelRequested]. Answered work stays, including a
     * lookahead that an answer has already raised.
     */
    fun dropLookahead(): Int = synchronized(lock) {
        val kept = ArrayList<ExplainWork>()
        var dropped = 0
        while (true) {
            val job = queue.poll() ?: break
            if (job.kind == ExplainKind.LOOKAHEAD) dropped += 1 else kept += job
        }
        offerAll(kept)
        val flying = inflight
        if (flying != null && flying.kind == ExplainKind.LOOKAHEAD && flying.turnId == null) {
            flying.cancelRequested = true
        }
        dropped
    }

    fun inflightWork(): ExplainWork? = synchronized(lock) { inflight }

    fun succeed(job: ExplainWork) = synchronized(lock) {
        if (inflight?.seq == job.seq) inflight = null
        failures = 0
    }

    /** Stop asked this lookahead to quit. Not a failure, so the breaker stays put. */
    fun abandon(job: ExplainWork) = synchronized(lock) {
        if (inflight?.seq == job.seq) inflight = null
    }

    fun fail(job: ExplainWork, fail: ExplainFail, now: Long = clock()): FailOutcome = synchronized(lock) {
        if (inflight?.seq == job.seq) inflight = null
        if (fail == ExplainFail.CANCEL) {
            return FailOutcome(retry = false, delayMs = 0, breakerOpen = now < pausedUntil)
        }
        failures += 1
        val opened = failures >= ExplainBackoff.BREAKER_AFTER
        if (opened) {
            pausedUntil = now + ExplainBackoff.BREAKER_PAUSE_MS
            val kept = ArrayList<ExplainWork>()
            while (true) {
                val queued = queue.poll() ?: break
                if (queued.kind != ExplainKind.LOOKAHEAD) kept += queued
            }
            offerAll(kept)
        }
        val canRetry = ExplainBackoff.retries(fail) && job.attempt < ExplainBackoff.MAX_RETRIES
        if (!canRetry) {
            return FailOutcome(retry = false, delayMs = 0, breakerOpen = opened || now < pausedUntil)
        }
        if (job.kind == ExplainKind.LOOKAHEAD && now < pausedUntil) {
            return FailOutcome(retry = false, delayMs = 0, breakerOpen = true)
        }
        val delay = ExplainBackoff.delayMs(job.attempt, randomUnit())
        queue.offer(
            ExplainWork(
                job.key, job.cardId, job.front, job.back, job.turnId, job.kind,
                job.seq, job.attempt + 1, now + delay
            )
        )
        FailOutcome(retry = true, delayMs = delay, breakerOpen = opened)
    }

    /** Jobs in poll order. */
    fun queued(): List<ExplainWork> = synchronized(lock) {
        queue.toList().sortedWith(compareBy({ rank(it.kind) }, { it.seq }))
    }

    fun size(): Int = synchronized(lock) { queue.size }

    /** How long the worker should sleep when [poll] returns null. */
    fun delayUntilReady(now: Long = clock()): Long = synchronized(lock) {
        var soonest: Long? = null
        for (job in queue) {
            if (job.notBefore <= now) return 0L
            val wait = job.notBefore - now
            if (soonest == null || wait < soonest) soonest = wait
        }
        soonest?.coerceIn(25L, 60_000L) ?: 60_000L
    }

    private fun drain(): ArrayList<ExplainWork> {
        val out = ArrayList<ExplainWork>()
        while (true) {
            val job = queue.poll() ?: break
            out += job
        }
        return out
    }

    private fun offerAll(jobs: List<ExplainWork>) {
        for (job in jobs) queue.offer(job)
    }

    private fun rank(kind: ExplainKind): Int = if (kind == ExplainKind.ANSWERED) 0 else 2
}
