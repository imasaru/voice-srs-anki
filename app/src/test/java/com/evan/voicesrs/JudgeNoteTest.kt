package com.evan.voicesrs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JudgeNoteTest {
    @After fun clear() = JudgeNote.clear()

    @Test
    fun `pending reason is taken once, only for the card and ease the judge gave`() {
        JudgeNote.put(7L, 2, "right option, but missed the pre-warm")
        assertNull(JudgeNote.take(8L, 2))
        assertEquals("right option, but missed the pre-warm", JudgeNote.take(7L, 2))
        assertNull(JudgeNote.take(7L, 2))        // consumed
        JudgeNote.put(7L, 2, "x")
        assertNull(JudgeNote.take(7L, 3))        // graded differently than the judge: no reason
        JudgeNote.put(9L, 3, "   ")
        assertNull(JudgeNote.take(9L, 3))        // blank reasons are not kept
        JudgeNote.put(5L, 1, "y")
        JudgeNote.drop(5L)
        assertNull(JudgeNote.take(5L, 1))
    }
}
