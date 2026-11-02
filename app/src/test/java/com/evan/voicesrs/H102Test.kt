package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H102Test {

    /** Dead neural server: block after one real failure, stay blocked (resume + next cards), unblock once health passes. */
    @Test
    fun `neural blocks after one failure and stays blocked until health passes`() {
        var edgeCalls = 0
        var blocked = false
        blocked = TtsFallback.route(true, blocked, neural = { edgeCalls++; false }, onDevice = { TtsFallback.Spoke.DONE })
        assertTrue(blocked)
        assertEquals(1, edgeCalls)
        // resume after focus loss + next cards: EdgeSpeaker must not be called again while blocked
        repeat(3) {
            blocked = TtsFallback.route(true, blocked, neural = { edgeCalls++; false }, onDevice = { TtsFallback.Spoke.DONE })
        }
        assertEquals(1, edgeCalls)
        assertTrue(blocked)
        // /health passed: refreshNeuralHealth sets neuralBlocked = !ok
        blocked = !true
        blocked = TtsFallback.route(true, blocked, neural = { edgeCalls++; true }, onDevice = { TtsFallback.Spoke.DONE })
        assertEquals(2, edgeCalls)
        assertFalse(blocked)
    }

    /** A user stop does not block; a real failure does; preferred-neural gate. */
    @Test
    fun `blockedAfter and useNeural gate the neural path`() {
        assertEquals(false, TtsFallback.blockedAfter(false, ok = false, stopped = true))
        assertEquals(true, TtsFallback.blockedAfter(false, ok = false, stopped = false))
        assertEquals(true, TtsFallback.blockedAfter(true, ok = true, stopped = false))
        assertEquals(true, TtsFallback.useNeural(true, false))
        assertEquals(false, TtsFallback.useNeural(true, true))
        assertEquals(false, TtsFallback.useNeural(false, false))
    }

    /** On-device highlight pieces are exact spoken.substring(ranges[i]), same as speakNeuralCard. */
    @Test
    fun `on-device parts match SentenceSplit ranges`() {
        val spoken = "Which call returns the row count?\n  df.count()\nAnswer A: df.count(). Answer B: len(df). Answer C: df.size()."
        val r = SentenceSplit.ranges(spoken)
        assertEquals(r.map { spoken.substring(it) }, TtsFallback.onDeviceParts(spoken))
        assertTrue(TtsFallback.onDeviceParts(spoken).any { it.startsWith("Answer B") })
        assertTrue(TtsFallback.onDeviceParts("   ").isEmpty())
    }

    /** Tap "show answer" while the question is still being read: announce, then read the answer. */
    @Test
    fun `show answer is announced when a tap cuts the question read`() {
        assertEquals(TtsFallback.AfterQuestion.ANNOUNCE_THEN_ANSWER,
            TtsFallback.afterQuestion(showEarly = true, cut = true, silent = false, thinkArmed = true))
        assertEquals(listOf("Showing answer", "Answer C: PIVOT."),
            TtsFallback.announceFirst(TtsFallback.AfterQuestion.ANNOUNCE_THEN_ANSWER, "Answer C: PIVOT."))
        assertFalse(TtsFallback.listens(TtsFallback.AfterQuestion.ANNOUNCE_THEN_ANSWER))
        // tap after the read already ended -> skip listen, just the answer
        assertEquals(TtsFallback.AfterQuestion.SKIP_LISTEN,
            TtsFallback.afterQuestion(showEarly = true, cut = false, silent = false, thinkArmed = true))
        assertFalse(TtsFallback.listens(TtsFallback.AfterQuestion.SKIP_LISTEN))
        assertEquals(listOf("Answer C: PIVOT."),
            TtsFallback.announceFirst(TtsFallback.AfterQuestion.SKIP_LISTEN, "Answer C: PIVOT."))
        // silent on -> skip listen
        assertEquals(TtsFallback.AfterQuestion.SKIP_LISTEN,
            TtsFallback.afterQuestion(showEarly = false, cut = false, silent = true, thinkArmed = true))
    }

    /** No tap: think-then-listen when think time is armed, else plain listen. */
    @Test
    fun `no tap thinks then listens or just listens`() {
        assertEquals(TtsFallback.AfterQuestion.THINK_LISTEN,
            TtsFallback.afterQuestion(false, false, false, thinkArmed = true))
        assertTrue(TtsFallback.listens(TtsFallback.AfterQuestion.THINK_LISTEN))
        assertEquals(TtsFallback.AfterQuestion.LISTEN,
            TtsFallback.afterQuestion(false, false, false, thinkArmed = false))
        assertTrue(TtsFallback.listens(TtsFallback.AfterQuestion.LISTEN))
    }

    /** Spoke result from the accepted + stopped flags. */
    @Test
    fun `spoke result from accepted and stopped flags`() {
        assertEquals(TtsFallback.Spoke.STOPPED, TtsFallback.spokeResult(accepted = true, stoppedFlag = true))
        assertEquals(TtsFallback.Spoke.DONE, TtsFallback.spokeResult(accepted = true, stoppedFlag = false))
        assertEquals(TtsFallback.Spoke.FAILED, TtsFallback.spokeResult(accepted = false, stoppedFlag = false))
    }

    /** Window cut off / recognizer destroyed with no final: own-words deadline text wins, else the latest non-blank partial. */
    @Test
    fun `cutoff keeps the latest partial when the window is cut off`() {
        assertEquals("course Delta table had", ListenWindow.cutoffText(null, listOf("course Delta table had")))
        assertEquals("good", ListenWindow.cutoffText(null, listOf(" good ")))
        assertEquals(VoiceAction.Grade(3), GradeMapper.parse("good"))
        assertEquals("own words", ListenWindow.cutoffText("own words", listOf("x")))
        assertEquals(null, ListenWindow.cutoffText(null, emptyList()))
        assertEquals(null, ListenWindow.cutoffText(null, listOf("")))
    }

    /** Recognizer destroyed + recreated with no new partial keeps the latest recorded partial. */
    @Test
    fun `cutoff across recognizer destroy and restart keeps the latest partial`() {
        var session: List<String> = emptyList()
        session = listOf("course Delta table")
        session = listOf("course Delta table had") // recognizer destroyed + recreated, no new partial
        assertEquals("course Delta table had", ListenWindow.cutoffText(null, session))
    }
}
