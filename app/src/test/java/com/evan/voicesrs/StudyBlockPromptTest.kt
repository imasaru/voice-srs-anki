package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class StudyBlockPromptTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun at(h: Int, m: Int): Long =
        java.time.ZonedDateTime.of(2026, 10, 27, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun paused(index: Int, elapsedMs: Long, goalMin: Int): StudyBlockStore.State =
        StudyBlockStore.State(
            index = index,
            elapsedMs = elapsedMs,
            goalMin = goalMin,
            dayKey = "2026-10-27",
            runStartMs = null,
            done = false
        )

    @Test
    fun `minutes rounds down`() {
        assertEquals(0, StudyBlockPrompt.minutes(paused(1, 59_999, 90), at(12, 0)))
        assertEquals(1, StudyBlockPrompt.minutes(paused(1, 60_000, 90), at(12, 0)))
        assertEquals(34, StudyBlockPrompt.minutes(paused(1, 34 * 60_000L + 59_000, 90), at(12, 0)))
    }

    @Test
    fun `offer is null when nothing to continue`() {
        // null state
        assertNull(StudyBlockPrompt.offer(null, at(11, 0), zone))

        // done block
        val done = StudyBlockStore.finish(
            StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false),
            at(10, 30)
        )
        assertNull(StudyBlockPrompt.offer(done, at(11, 0), zone))

        // paused block with 0 elapsed
        val zero = StudyBlockStore.pause(
            StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false),
            at(10, 0)
        )
        assertNull(StudyBlockPrompt.offer(zero, at(11, 0), zone))

        // block from yesterday's Anki day, checked at 04:00
        val yesterday = StudyBlockStore.State(
            index = 1,
            elapsedMs = 30 * 60_000L,
            goalMin = 90,
            dayKey = "2026-10-26",
            runStartMs = null,
            done = false
        )
        assertNull(StudyBlockPrompt.offer(yesterday, at(4, 0), zone))
    }

    @Test
    fun `offer for paused block shows goal progress`() {
        val s = StudyBlockStore.pause(
            StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false),
            at(10, 34)
        )
        val offer = StudyBlockPrompt.offer(s, at(11, 0), zone)
        assertEquals("Continue block (34 of 90)", offer?.continueLabel)
        assertEquals(StudyBlockPrompt.NEW_LABEL, offer?.newLabel)
    }

    @Test
    fun `offer with goal zero shows minutes only`() {
        val s = StudyBlockStore.pause(
            StudyBlockStore.start(null, at(10, 0), zone, goalMin = 0, newBlock = false),
            at(10, 34)
        )
        val offer = StudyBlockPrompt.offer(s, at(11, 0), zone)
        assertEquals("Continue block (34 min)", offer?.continueLabel)
    }

    @Test
    fun `offer for running block uses elapsedAt`() {
        val s = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        val offer = StudyBlockPrompt.offer(s, at(10, 20), zone)
        assertEquals("Continue block (20 of 90)", offer?.continueLabel)
    }

    @Test
    fun `blockLine shows goal progress or minutes only`() {
        val withGoal = paused(2, 34 * 60_000L, 90)
        assertEquals("Block 2: 34 of 90 min", StudyBlockPrompt.blockLine(withGoal, at(12, 0)))

        val noGoal = paused(2, 34 * 60_000L, 0)
        assertEquals("Block 2: 34 min", StudyBlockPrompt.blockLine(noGoal, at(12, 0)))
    }

    @Test
    fun `choose continue resumes the block`() {
        val s = paused(1, 34 * 60_000L, 90)
        val next = StudyBlockPrompt.choose(s, at(11, 0), zone, goalMin = 90, continueChosen = true)
        assertEquals(1, next.index)
        assertEquals(34 * 60_000L, next.elapsedMs)
        assertEquals(at(11, 0), next.runStartMs)
    }

    @Test
    fun `choose new starts a fresh block`() {
        val s = paused(1, 34 * 60_000L, 90)
        val next = StudyBlockPrompt.choose(s, at(11, 0), zone, goalMin = 60, continueChosen = false)
        assertEquals(2, next.index)
        assertEquals(0L, next.elapsedMs)
        assertEquals(60, next.goalMin)
        assertEquals(at(11, 0), next.runStartMs)
    }
}
