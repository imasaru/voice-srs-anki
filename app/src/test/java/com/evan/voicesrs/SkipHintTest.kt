package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkipHintTest {

    /** A thinking (question shown, answer not yet revealed) shows the answer hint. */
    @Test
    fun `a thinking shows the answer hint`() {
        assertEquals(3, SkipQueue.guide(true, false, false))
    }

    /** After the answer is revealed, the hint says to give a grade. */
    @Test
    fun `b after reveal says a grade`() {
        assertEquals(1, SkipQueue.guide(true, true, false))
    }

    /** Silent before reveal: the hint is hidden. */
    @Test
    fun `c silent before reveal hidden`() {
        assertEquals(0, SkipQueue.guide(true, false, true))
    }

    /** Silent after reveal: the hint says to tap a grade. */
    @Test
    fun `d silent after reveal tap`() {
        assertEquals(2, SkipQueue.guide(true, true, true))
    }

    /** No question on screen: the hint is hidden. */
    @Test
    fun `e no question hidden`() {
        assertEquals(0, SkipQueue.guide(false, false, false))
    }

    /** The answer-guide string is present in the app resources. */
    @Test
    fun `f strings`() {
        val text = findStrings().readText()
        assertTrue(
            text.contains("""Say your answer, "repeat", or "skip"""")
        )
    }

    private fun findStrings(): File {
        var d = File(".").absoluteFile
        while (d != null) {
            val cand = File(d, "app/src/main/res/values/strings.xml")
            if (cand.exists()) return cand
            d = d.parentFile
        }
        return File("app/src/main/res/values/strings.xml")
    }
}
