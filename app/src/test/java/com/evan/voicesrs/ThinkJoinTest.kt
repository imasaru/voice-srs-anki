package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulates the thinkForAnswer loop decisions with the pure ThinkBuffer +
 * ThinkTime parts. The DrillService wiring keeps these shapes: SETTLE buffers
 * and waits, REVEAL reveals the joined text, ACT leaves the loop, and
 * RESTART_QUIET restarts the mic without charging an attempt.
 */
class ThinkJoinTest {

    private fun next(hear: ThinkTime.Hear, bufferEmpty: Boolean, remainingMs: Long, settling: Boolean) =
        ThinkTime.nextStep(hear, bufferEmpty, remainingMs, settling)

    @Test
    fun `a two fragments reveal once joined`() {
        val buffer = ThinkBuffer()
        var settling = false

        // "the capital" is an answer: it settles into the buffer.
        assertEquals(ThinkTime.Step.SETTLE, next(ThinkTime.classify("the capital"), buffer.isEmpty(), 10_000L, settling))
        buffer.append("the capital")
        settling = true

        // "is canberra" settles too; the buffer joins them.
        assertEquals(ThinkTime.Step.SETTLE, next(ThinkTime.classify("is canberra"), buffer.isEmpty(), 10_000L, settling))
        buffer.append("is canberra")

        // Silence while settled reveals the joined text.
        assertEquals(ThinkTime.Step.REVEAL, next(ThinkTime.classify(""), buffer.isEmpty(), 10_000L, settling))
        assertEquals("the capital is canberra", buffer.text())
    }

    @Test
    fun `b skip mid think clears`() {
        val buffer = ThinkBuffer()

        // An answer settles into the buffer.
        assertEquals(ThinkTime.Step.SETTLE, next(ThinkTime.classify("some answer"), buffer.isEmpty(), 10_000L, settling = false))
        buffer.append("some answer")

        // A skip is ACT; the caller clears the buffer before leaving the loop.
        assertEquals(ThinkTime.Step.ACT, next(ThinkTime.classify("skip"), buffer.isEmpty(), 10_000L, settling = false))
        buffer.clear()
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `c nothing heard runs to deadline`() {
        val buffer = ThinkBuffer()

        // Silence restarts the mic quietly without charging an attempt.
        assertEquals(ThinkTime.Step.RESTART_QUIET, next(ThinkTime.classify(""), buffer.isEmpty(), 5_000L, settling = false))
        assertTrue(buffer.isEmpty())

        // At the deadline with an empty buffer the loop reveals.
        assertEquals(ThinkTime.Step.REVEAL, next(ThinkTime.classify(""), buffer.isEmpty(), 0L, settling = false))
        assertTrue(buffer.isEmpty())
    }
}
