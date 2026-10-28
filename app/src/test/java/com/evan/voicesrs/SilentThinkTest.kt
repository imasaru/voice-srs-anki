package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentThinkTest {

    @Test
    fun `a user silent during think time keeps the timer and does not reveal`() {
        val plan = SilentTap.plan(
            wantOn = true,
            wasOn = false,
            hasQuestion = true,
            answerShown = false,
            fromUser = true,
            thinkRunning = true
        )
        assertTrue(plan.silent)
        assertTrue(plan.cancelMic)
        assertFalse(plan.stopThinkTimer)
        assertFalse(plan.revealAnswer)
        assertTrue(plan.showAnswerButton)
    }

    @Test
    fun `b without think time silent still reveals`() {
        val plan = SilentTap.plan(
            wantOn = true,
            wasOn = false,
            hasQuestion = true,
            answerShown = false,
            fromUser = true,
            thinkRunning = false
        )
        assertTrue(plan.revealAnswer)
        assertTrue(plan.stopThinkTimer)
    }

    @Test
    fun `c give-up during think time is unchanged`() {
        val plan = SilentTap.plan(
            wantOn = true,
            wasOn = false,
            hasQuestion = true,
            answerShown = false,
            fromUser = false,
            thinkRunning = true
        )
        assertFalse(plan.revealAnswer)
        assertTrue(plan.stopThinkTimer)
    }

    @Test
    fun `d timer keeps running until the deadline`() {
        assertTrue(SilentTap.thinkContinues(true, 10_000L, 9_999L))
        assertFalse(SilentTap.thinkContinues(true, 10_000L, 10_000L))
        assertFalse(SilentTap.thinkContinues(true, 0L, 5L))
        assertFalse(SilentTap.thinkContinues(false, 10_000L, 1L))
    }

    @Test
    fun `e think loop no longer gives up on silent`() {
        val src = File("src/main/java/com/evan/voicesrs/DrillService.kt")
        assertTrue("source file missing: ${src.absolutePath}", src.isFile)
        val text = src.readText()
        val idx = text.indexOf("fun thinkForAnswer(")
        assertTrue("thinkForAnswer not found", idx >= 0)
        val body = thinkForAnswerBody(text, idx)
        assertFalse(
            "thinkForAnswer must not give up on silent",
            body.contains("if (sessionSilent) return ThinkResult.Again")
        )
        val hits = body.split("SilentTap.thinkContinues(").size - 1
        assertEquals("thinkForAnswer must consult thinkContinues twice", 2, hits)
    }

    /** Body of the function starting at [idx], matched by brace depth. */
    private fun thinkForAnswerBody(text: String, idx: Int): String {
        val start = text.indexOf('{', idx)
        var depth = 0
        var i = start
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (depth == 0) return text.substring(start, i + 1) else depth--
            }
            i++
        }
        throw IllegalStateException("unbalanced braces in thinkForAnswer")
    }
}
