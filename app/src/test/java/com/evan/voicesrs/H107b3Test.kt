package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class H107b3Test {

    @Test
    fun wrong_announces_the_answer() {
        assertEquals(TapAnswer.LetterResult(1, "Not quite. The answer is B."), TapAnswer.letterResult("A", "B"))
    }

    @Test
    fun case_insensitive_correct() {
        assertEquals(TapAnswer.LetterResult(3, "Correct."), TapAnswer.letterResult("b", "B"))
    }

    @Test
    fun trims_whitespace() {
        assertEquals(TapAnswer.LetterResult(3, "Correct."), TapAnswer.letterResult(" C ", "c"))
    }

    @Test
    fun line_uses_trimmed_upper_answer() {
        assertEquals("Not quite. The answer is A.", TapAnswer.letterResult("D", " a ").line)
    }
}
