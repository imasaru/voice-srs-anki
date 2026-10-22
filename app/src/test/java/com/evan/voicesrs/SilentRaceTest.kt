package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentRaceTest {
    @Test
    fun `tap during mic startup cannot reopen the microphone`() {
        var gate = ListenGate.State(silent = false, epoch = 4)
        val posted = ListenGate.postedEpoch(gate)
        assertEquals(4, posted)
        assertTrue(ListenGate.mayOpenMic(gate, posted!!))
        // The recognizer start is already posted. The user turns Silent on.
        gate = ListenGate.toggle(gate, true)
        assertTrue(gate.silent)
        assertEquals(5, gate.epoch)
        assertNull(ListenGate.postedEpoch(gate))
        assertFalse(ListenGate.mayOpenMic(gate, posted))
        // The check after startListening returns still refuses that epoch.
        assertFalse(ListenGate.mayOpenMic(gate, posted))
        // A repeated write does not burn the epoch, so an in-flight start can finish.
        assertEquals(gate, ListenGate.toggle(gate, true))
        // Silent off resumes on a new epoch. The abandoned start stays closed.
        gate = ListenGate.toggle(gate, false)
        assertFalse(gate.silent)
        assertEquals(6, gate.epoch)
        assertTrue(ListenGate.mayOpenMic(gate, 6))
        assertFalse(ListenGate.mayOpenMic(gate, posted))
        assertFalse(ListenGate.mayOpenMic(gate, 5))
    }

    @Test
    fun `stale ui snapshot after the tap leaves the switch on`() {
        var model = SwitchBoard.Model(on = false, generation = 2)
        val capturedBeforeTap = SwitchBoard.Paint(on = false, generation = 2)
        model = SwitchBoard.userTap(model, true)
        assertTrue(model.on)
        assertEquals(3, model.generation)
        val kept = SwitchBoard.reconcile(model, capturedBeforeTap)
        assertTrue(kept.on)
        assertEquals(3, kept.generation)
        // The service snapshot from this same tap is not stale.
        val fresh = SwitchBoard.reconcile(kept, SwitchBoard.Paint(on = true, generation = 3))
        assertTrue(fresh.on)
        assertEquals(3, fresh.generation)
        // A later service paint, including give-up, can still move the switch.
        val givenUp = SwitchBoard.reconcile(fresh, SwitchBoard.Paint(on = true, generation = 4))
        assertTrue(givenUp.on)
        assertEquals(4, givenUp.generation)
    }

    @Test
    fun `user silent on the question shows ratings and give-up does not`() {
        val duringListen = SilentTap.plan(
            wantOn = true,
            wasOn = false,
            hasQuestion = true,
            answerShown = false,
            fromUser = true
        )
        assertTrue(duringListen.silent)
        assertTrue(duringListen.cancelMic)
        assertTrue(duringListen.stopThinkTimer)
        assertTrue(duringListen.revealAnswer)
        assertTrue(duringListen.showRating)
        assertFalse(duringListen.showAnswerButton)

        val duringThink = SilentTap.plan(
            wantOn = true,
            wasOn = false,
            hasQuestion = true,
            answerShown = false,
            fromUser = true
        )
        assertTrue(duringThink.stopThinkTimer)
        assertTrue(duringThink.cancelMic)
        assertTrue(duringThink.revealAnswer)

        val giveUp = SilentTap.plan(
            wantOn = true,
            wasOn = false,
            hasQuestion = true,
            answerShown = false,
            fromUser = false
        )
        assertTrue(giveUp.cancelMic)
        assertTrue(giveUp.stopThinkTimer)
        assertFalse(giveUp.revealAnswer)
        assertFalse(giveUp.showRating)
        assertTrue(giveUp.showAnswerButton)

        val off = SilentTap.plan(
            wantOn = false,
            wasOn = true,
            hasQuestion = true,
            answerShown = false,
            fromUser = true
        )
        assertFalse(off.silent)
        assertFalse(off.cancelMic)
        assertFalse(off.stopThinkTimer)
        assertFalse(off.revealAnswer)
        assertTrue(off.showAnswerButton)
        assertEquals(CardResume.READ_QUESTION, AudioGate.resume(false, cardUp = true, answerShown = false))
        assertEquals(CardResume.READ_ANSWER, AudioGate.resume(false, cardUp = true, answerShown = true))
        assertEquals(CardResume.HOLD, AudioGate.resume(true, cardUp = true, answerShown = false))
    }
}
