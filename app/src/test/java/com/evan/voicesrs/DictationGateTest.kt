package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationGateTest {
    @Test
    fun `dictation releases the drill mic and then starts its own recognizer`() {
        val listening = DictationGate.beginDictation(
            DictationGate.State(DictationGate.Owner.DRILL, drillListening = true)
        )
        assertEquals(
            listOf(DictationGate.Step.RELEASE_DRILL, DictationGate.Step.START_DICTATION),
            listening.steps
        )
        assertTrue(listening.steps.indexOf(DictationGate.Step.RELEASE_DRILL) <
            listening.steps.indexOf(DictationGate.Step.START_DICTATION))
        assertEquals(DictationGate.Owner.DICTATION, listening.state.owner)
        assertEquals(false, listening.state.drillListening)

        val idle = DictationGate.beginDictation(DictationGate.State())
        assertEquals(listOf(DictationGate.Step.START_DICTATION), idle.steps)
        assertEquals(DictationGate.Owner.DICTATION, idle.state.owner)
    }

    @Test
    fun `pause never cancels dictation and still cancels the drill listener`() {
        val during = DictationGate.pause(
            DictationGate.State(DictationGate.Owner.DICTATION, drillListening = false)
        )
        assertTrue(during.steps.isEmpty())
        assertEquals(DictationGate.Owner.DICTATION, during.state.owner)

        val drill = DictationGate.pause(
            DictationGate.State(DictationGate.Owner.DRILL, drillListening = true)
        )
        assertEquals(listOf(DictationGate.Step.CANCEL_DRILL), drill.steps)
        assertEquals(DictationGate.Owner.NONE, drill.state.owner)
    }

    @Test
    fun `an error keeps a partial and a total miss stays a miss`() {
        assertEquals(
            DictationGate.Result.Text("partial words"),
            DictationGate.result(finalText = null, partial = "partial words", error = true)
        )
        assertEquals(
            DictationGate.Result.Miss,
            DictationGate.result(finalText = " ", partial = "", error = true)
        )
        assertEquals(
            DictationGate.Result.Text("final words"),
            DictationGate.result(finalText = "final words", partial = "partial", error = false)
        )
        assertEquals(
            DictationGate.Result.Empty,
            DictationGate.result(finalText = null, partial = null, error = false)
        )
    }
}
