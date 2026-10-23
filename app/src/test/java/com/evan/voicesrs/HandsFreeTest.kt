package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeTest {
    @Test
    fun `auto advance runs unless the setting is off or chat is paused`() {
        assertTrue(HandsFree.autoAdvance(enabled = true, chatPaused = false))
        assertFalse(HandsFree.autoAdvance(enabled = true, chatPaused = true))
        assertFalse(HandsFree.autoAdvance(enabled = false, chatPaused = false))
    }

    @Test
    fun `ready prompt is optional and only when hands free is on`() {
        assertEquals("Ready?", HandsFree.prompt(enabled = true, readyOn = true))
        assertNull(HandsFree.prompt(enabled = true, readyOn = false))
        assertNull(HandsFree.prompt(enabled = false, readyOn = true))
        assertEquals(HandsFree.READY_PROMPT, HandsFree.prompt(true, true))
    }

    @Test
    fun `yes ready next and go accept the prompt`() {
        assertTrue(HandsFree.accepted("yes"))
        assertTrue(HandsFree.accepted("Ready"))
        assertTrue(HandsFree.accepted(" next "))
        assertTrue(HandsFree.accepted("ok"))
        assertFalse(HandsFree.accepted("no"))
        assertFalse(HandsFree.accepted(null))
        assertFalse(HandsFree.accepted("   "))
    }

    @Test
    fun `wait after a grade`() {
        assertEquals(HandsFree.Wait.NONE, HandsFree.waitAfterGrade(handsFree = true, readyPrompt = false))
        assertEquals(HandsFree.Wait.READY_PROMPT, HandsFree.waitAfterGrade(true, true))
        assertEquals(HandsFree.Wait.UNTIL_NEXT, HandsFree.waitAfterGrade(false, true))
        assertEquals(HandsFree.Wait.UNTIL_NEXT, HandsFree.waitAfterGrade(false, false))
        assertEquals(0L, HandsFree.waitMs(HandsFree.Wait.NONE))
        assertEquals(HandsFree.READY_WAIT_MS, HandsFree.waitMs(HandsFree.Wait.READY_PROMPT))
        assertEquals("Ready?", HandsFree.spokenFor(HandsFree.Wait.READY_PROMPT))
        assertEquals("Say next when you are ready.", HandsFree.spokenFor(HandsFree.Wait.UNTIL_NEXT))
    }
}
