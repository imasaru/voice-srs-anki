package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainQueueLogicTest {
    private class Clock {
        var now = 0L
    }

    private fun queue(clock: Clock = Clock(), unit: Double = 0.0): Pair<ExplainQueueLogic, Clock> {
        return ExplainQueueLogic({ clock.now }, { unit }) to clock
    }

    /** One worker: finish the in-flight job before the next poll. */
    private fun ExplainQueueLogic.take(): ExplainWork {
        val job = poll()!!
        succeed(job)
        return job
    }

    private fun ExplainQueueLogic.add(
        noteId: Long,
        kind: ExplainKind,
        hash: String = "h$noteId",
        turnId: Long? = if (kind == ExplainKind.ANSWERED) noteId else null
    ): EnqueueEffect {
        return enqueue(ExplainKey(noteId, hash), noteId, "f", "b", turnId, kind)
    }

    @Test
    fun `answered outranks an earlier lookahead and fifo breaks ties`() {
        val (q, _) = queue()
        q.add(1, ExplainKind.LOOKAHEAD)
        q.add(2, ExplainKind.LOOKAHEAD)
        q.add(3, ExplainKind.ANSWERED)
        q.add(4, ExplainKind.ANSWERED)
        assertEquals(3L, q.take().key.noteId)
        assertEquals(4L, q.take().key.noteId)
        assertEquals(1L, q.take().key.noteId)
        assertEquals(2L, q.take().key.noteId)
        assertNull(q.poll())
    }

    @Test
    fun `the same note and hash is one job`() {
        val (q, _) = queue()
        assertEquals(EnqueueEffect.ADDED, q.add(1, ExplainKind.LOOKAHEAD))
        assertEquals(EnqueueEffect.DUPLICATE, q.add(1, ExplainKind.LOOKAHEAD))
        assertEquals(1, q.size())
        val job = q.poll()!!
        assertEquals(EnqueueEffect.DUPLICATE, q.add(1, ExplainKind.LOOKAHEAD))
        assertEquals(0, q.size())
        assertSame(job, q.inflightWork())
    }

    @Test
    fun `raising a queued lookahead puts it behind answered work already waiting`() {
        val (q, _) = queue()
        q.add(1, ExplainKind.LOOKAHEAD)
        q.add(2, ExplainKind.ANSWERED)
        assertEquals(EnqueueEffect.RAISED, q.add(1, ExplainKind.ANSWERED, turnId = 9))
        val first = q.take()
        val second = q.take()
        assertEquals(2L, first.key.noteId)
        assertEquals(1L, second.key.noteId)
        assertEquals(ExplainKind.ANSWERED, second.kind)
        assertEquals(9L, second.turnId)
    }

    @Test
    fun `raising the in flight lookahead keeps the same job`() {
        val (q, _) = queue()
        q.add(1, ExplainKind.LOOKAHEAD)
        val job = q.poll()!!
        assertEquals(EnqueueEffect.RAISED, q.add(1, ExplainKind.ANSWERED, turnId = 4))
        assertSame(job, q.inflightWork())
        assertEquals(ExplainKind.ANSWERED, job.kind)
        assertEquals(4L, job.turnId)
        q.dropLookahead()
        assertFalse(job.cancelRequested)
    }

    @Test
    fun `stop drops queued lookahead and cancels only a pure in flight lookahead`() {
        val (q, _) = queue()
        q.add(1, ExplainKind.LOOKAHEAD)
        val flying = q.poll()!!
        q.add(2, ExplainKind.ANSWERED)
        q.add(3, ExplainKind.LOOKAHEAD)
        assertEquals(1, q.dropLookahead())
        assertTrue(flying.cancelRequested)
        assertEquals(listOf(2L), q.queued().map { it.key.noteId })

        val answered = queue()
        answered.first.add(8, ExplainKind.ANSWERED)
        val kept = answered.first.poll()!!
        answered.first.dropLookahead()
        assertFalse(kept.cancelRequested)
        assertSame(kept, answered.first.inflightWork())
    }

    @Test
    fun `a ready lookahead runs while a higher priority job is delayed`() {
        val (q, clock) = queue()
        q.add(1, ExplainKind.ANSWERED)
        val job = q.poll()!!
        val outcome = q.fail(job, ExplainFail.IO)
        assertTrue(outcome.retry)
        assertEquals(5_000L, outcome.delayMs)
        q.add(2, ExplainKind.LOOKAHEAD)
        val next = q.poll()!!
        assertEquals(ExplainKind.LOOKAHEAD, next.kind)
        q.succeed(next)
        clock.now = 5_000
        assertEquals(1L, q.poll()!!.key.noteId)
    }

    @Test
    fun `io and timeouts retry twice then stop and jitter is applied`() {
        val (q, clock) = queue(unit = 0.5)
        q.add(1, ExplainKind.ANSWERED)
        val first = q.poll()!!
        val wait1 = q.fail(first, ExplainFail.IO)
        assertTrue(wait1.retry)
        assertEquals(5_500L, wait1.delayMs)
        clock.now = 5_500
        val second = q.poll()!!
        assertEquals(1, second.attempt)
        val wait2 = q.fail(second, ExplainFail.TIMEOUT)
        assertTrue(wait2.retry)
        assertEquals(20_500L, wait2.delayMs)
        clock.now = 5_500 + 20_500
        val third = q.poll()!!
        assertEquals(2, third.attempt)
        val stop = q.fail(third, ExplainFail.SERVER)
        assertFalse(stop.retry)
        assertEquals(0, q.size())
    }

    @Test
    fun `a client error is not retried`() {
        val (q, _) = queue()
        q.add(1, ExplainKind.ANSWERED)
        val job = q.poll()!!
        val outcome = q.fail(job, ExplainFail.CLIENT)
        assertFalse(outcome.retry)
        assertEquals(0, q.size())
        assertFalse(ExplainBackoff.retries(ExplainFail.CLIENT))
        assertFalse(ExplainBackoff.retries(ExplainFail.OTHER))
        assertTrue(ExplainBackoff.retries(ExplainFail.IO))
    }

    @Test
    fun `three failures open the breaker drop lookahead and keep answered`() {
        val (q, clock) = queue()
        clock.now = 1_000
        q.add(1, ExplainKind.ANSWERED)
        var job = q.poll()!!
        q.fail(job, ExplainFail.IO)
        clock.now = 1_000 + 5_000
        job = q.poll()!!
        q.fail(job, ExplainFail.TIMEOUT)
        clock.now = 6_000 + 20_000
        job = q.poll()!!
        q.add(2, ExplainKind.LOOKAHEAD)
        q.add(3, ExplainKind.ANSWERED)
        val outcome = q.fail(job, ExplainFail.IO)
        assertFalse(outcome.retry)
        assertTrue(outcome.breakerOpen)
        assertFalse(q.lookaheadOpen())
        assertEquals(listOf(3L), q.queued().map { it.key.noteId })
        assertEquals(EnqueueEffect.DROPPED, q.add(4, ExplainKind.LOOKAHEAD))
        assertEquals(EnqueueEffect.ADDED, q.add(5, ExplainKind.ANSWERED))
        val kept = q.poll()!!
        q.succeed(kept)
        assertFalse(q.lookaheadOpen())
        clock.now += ExplainBackoff.BREAKER_PAUSE_MS
        assertTrue(q.lookaheadOpen())
        assertEquals(EnqueueEffect.ADDED, q.add(6, ExplainKind.LOOKAHEAD))
    }

    @Test
    fun `cancel does not trip the breaker`() {
        val (q, clock) = queue()
        q.add(1, ExplainKind.ANSWERED)
        var job = q.poll()!!
        q.fail(job, ExplainFail.IO)
        clock.now = 5_000
        job = q.poll()!!
        q.fail(job, ExplainFail.IO)
        clock.now = 5_000 + 20_000
        job = q.poll()!!
        val outcome = q.fail(job, ExplainFail.CANCEL)
        assertFalse(outcome.retry)
        assertFalse(outcome.breakerOpen)
        assertTrue(q.lookaheadOpen())
        assertEquals(EnqueueEffect.ADDED, q.add(2, ExplainKind.LOOKAHEAD))
    }

    @Test
    fun `lookahead skips cached cards and refreshes every ten`() {
        assertFalse(LookaheadWindow.shouldRefresh(9))
        assertTrue(LookaheadWindow.shouldRefresh(10))
        assertEquals(0, LookaheadWindow.clamp(-1))
        assertEquals(2, LookaheadWindow.clamp(2))
        assertEquals(5, LookaheadWindow.clamp(9))

        val cards = (1L..6L).map { AheadCard(it, it + 100, "F$it", "Back") }
        val cached = cards.take(3).map { card ->
            ExplainKey(card.noteKey, ContentHash.of(PromptBuilder.PROMPT_VERSION, card.front, card.back))
        }.toSet()
        val next = LookaheadPicker.nextAfter(cards, startExclusive = -1, n = 2, cached = cached)
        assertEquals(listOf(4L, 5L), next.map { it.cardId })
        val unseen = LookaheadPicker.nextUnseen(cards, seen = setOf(4L), n = 2, cached = cached)
        assertEquals(listOf(5L, 6L), unseen.map { it.cardId })
        assertTrue(LookaheadPicker.nextAfter(cards, 0, 0, emptySet()).isEmpty())
    }
}
