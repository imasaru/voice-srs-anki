package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

/** finalOrPartial: fall back to the first non-blank partial when the final is blank. */
class ListenFinalPartialTest {

    @Test
    fun a_finalBlankKeepsPartialGood() {
        assertEquals("good", ListenWindow.finalOrPartial(listOf(""), listOf("good")))
    }

    @Test
    fun b_finalEmptyKeepsFirstNonBlankPartial() {
        val result = ListenWindow.finalOrPartial(
            emptyList(),
            listOf(" that was a good answer", " that was good answer")
        )
        assertEquals("that was a good answer", result)
        assertEquals(VoiceAction.Grade(3), GradeMapper.parse(result))
    }

    @Test
    fun c_finalBlankKeepsPartialNo() {
        assertEquals("no", ListenWindow.finalOrPartial(listOf(""), listOf("no")))
        assertEquals(SilentAsk.Reply.NO, SilentAsk.parse("no"))
    }

    @Test
    fun d_finalNonBlankWinsOverPartial() {
        assertEquals("easy", ListenWindow.finalOrPartial(listOf("easy"), listOf("good")))
    }

    @Test
    fun e_allBlankIsEmpty() {
        assertEquals("", ListenWindow.finalOrPartial(listOf(""), listOf("")))
    }
}
