package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ExamErrorTest {

    @Test
    fun `a failure without a message names the exception`() {
        val msg = ExamError.format(IOException())
        assertEquals("Exam apply failed (IOException). Try again.", msg)
    }

    @Test
    fun `a failure with a message includes the detail`() {
        val msg = ExamError.format(IOException("Connection timed out"))
        assertEquals("Exam apply failed (IOException): Connection timed out", msg)
    }

    @Test
    fun `a blank message degrades to the exception name`() {
        val msg = ExamError.format(IOException("   "))
        assertEquals("Exam apply failed (IOException). Try again.", msg)
    }

    @Test
    fun `the message leads with a stable prefix`() {
        assertTrue(ExamError.format(RuntimeException("boom")).startsWith("Exam apply failed"))
    }
}
