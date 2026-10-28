package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapWhilePausedTest {

    @Test
    fun `a paused tap resumes`() {
        assertEquals(ChatPause.Action.RESUME, ChatPause.tapPlan(paused = true, typing = false))
    }

    @Test
    fun `b typing keeps the pause`() {
        assertEquals(null, ChatPause.tapPlan(paused = true, typing = true))
    }

    @Test
    fun `c not paused just takes the letter`() {
        assertEquals(ChatPause.Action.OTHER, ChatPause.tapPlan(paused = false, typing = false))
        assertEquals(ChatPause.Action.OTHER, ChatPause.tapPlan(paused = false, typing = true))
    }

    @Test
    fun `d submitLetter uses the existing resume path`() {
        val src = File("src/main/java/com/evan/voicesrs/DrillService.kt")
        assertTrue("source file missing: ${src.absolutePath}", src.isFile)
        val text = src.readText()
        val idx = text.indexOf("fun submitLetter(letter: String")
        assertTrue("submitLetter not found", idx >= 0)
        val end = text.indexOf("}", idx)
        val body = text.substring(idx, end)
        assertTrue("must consult tapPlan", body.contains("ChatPause.tapPlan("))
        assertTrue("must resume via leaveChatPause", body.contains("leaveChatPause(ChatPause.Action.RESUME)"))
        assertTrue("must accept the letter", body.contains("acceptLetter(letter)"))
        val acceptIdx = body.indexOf("acceptLetter(letter)")
        val leaveIdx = body.indexOf("leaveChatPause(ChatPause.Action.RESUME)")
        assertTrue(
            "acceptLetter must run before leaveChatPause",
            acceptIdx >= 0 && leaveIdx >= 0 && acceptIdx < leaveIdx
        )
    }
}
