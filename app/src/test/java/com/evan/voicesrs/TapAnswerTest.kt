package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapAnswerTest {
    private val letters = setOf("A", "B", "C", "D")
    private val card = QuestionCard.parse("Stem\nA. one\nB. two\nC. three\nD. four")

    @Test
    fun `a tap records the original letter and never grades`() {
        val right = TapAnswer.onTap("B", correct = "B", revealed = false)
        assertEquals("B", right?.letter)
        assertTrue(right?.correct == true)
        assertEquals(3, right?.suggestedEase)
        assertFalse(right?.autoGrade == true)
        assertFalse(right?.stillTappable == true)

        val wrong = TapAnswer.onTap("c", correct = "B", revealed = false)
        assertEquals("C", wrong?.letter)
        assertFalse(wrong?.correct == true)
        assertEquals(1, wrong?.suggestedEase)
        assertFalse(wrong?.autoGrade == true)
    }

    @Test
    fun `rows are not tappable after reveal`() {
        assertNull(TapAnswer.onTap("B", "B", revealed = true))
        assertNull(TapAnswer.onSpoken("bee", letters, "B", revealed = true))
        assertFalse(TapAnswer.tappable(mcq = true, revealed = true))
        assertTrue(TapAnswer.tappable(mcq = true, revealed = false))
        assertFalse(TapAnswer.tappable(mcq = false, revealed = false))
    }

    @Test
    fun `spoken letters include option B and bee`() {
        assertEquals("B", TapAnswer.parseSpoken("B", letters))
        assertEquals("B", TapAnswer.parseSpoken("option B", letters))
        assertEquals("B", TapAnswer.parseSpoken("bee", letters))
        assertEquals("B", TapAnswer.parseSpoken("Option b.", letters))
        assertEquals("B", TapAnswer.parseSpoken("the bee", letters))
        assertEquals("C", TapAnswer.parseSpoken("see", letters))
        assertNull(TapAnswer.parseSpoken("I think B", letters))
        assertNull(TapAnswer.parseSpoken("bee movie", letters))
        assertNull(TapAnswer.parseSpoken("B", setOf("A", "C")))
        val spoken = TapAnswer.onSpoken("bee", letters, "B", revealed = false)
        assertEquals("B", spoken?.letter)
        assertTrue(spoken?.correct == true)
        assertFalse(spoken?.autoGrade == true)
    }

    @Test
    fun `wrong row gets a cross and the correct row keeps the check`() {
        val wrong = QuestionCard.present(card, "B", revealed = true, chosen = "A")
        assertTrue(wrong.options[1].correct)
        assertFalse(wrong.options[1].chosen)
        assertTrue(wrong.options[0].chosen)
        assertEquals("B. two ✓", QuestionCard.optionRow(wrong.options[1]))
        assertEquals("A. one ✗", QuestionCard.optionRow(wrong.options[0]))
        assertFalse(QuestionCard.optionRow(wrong.options[1]).contains("✗"))

        val right = QuestionCard.present(card, "B", revealed = true, chosen = "B")
        assertTrue(right.options[1].correct)
        assertFalse(right.options[1].chosen)
        assertEquals("B. two ✓", QuestionCard.optionRow(right.options[1]))
        assertFalse(QuestionCard.optionRow(right.options[1]).contains("✗"))
    }

    @Test
    fun `the thread line names the letter`() {
        assertEquals("YOUR ANSWER: B", TapAnswer.heading("B"))
        assertEquals("YOUR ANSWER: B", TapAnswer.heading("bee"))
        assertEquals(QuestionCard.ANSWER_RATING, TapAnswer.heading("a filter on the table"))
        assertEquals(QuestionCard.ANSWER_RATING, TapAnswer.heading(""))
    }

    @Test
    fun `cutsSpeech only when the drill runs and speech is not paused or silent`() {
        assertTrue(TapAnswer.cutsSpeech(drillRunning = true, chatPaused = false, silentSession = false))
        assertFalse(TapAnswer.cutsSpeech(drillRunning = false, chatPaused = false, silentSession = false))
        assertFalse(TapAnswer.cutsSpeech(drillRunning = true, chatPaused = true, silentSession = false))
        assertFalse(TapAnswer.cutsSpeech(drillRunning = true, chatPaused = false, silentSession = true))
        assertFalse(TapAnswer.cutsSpeech(drillRunning = false, chatPaused = true, silentSession = false))
        assertFalse(TapAnswer.cutsSpeech(drillRunning = false, chatPaused = false, silentSession = true))
        assertFalse(TapAnswer.cutsSpeech(drillRunning = true, chatPaused = true, silentSession = true))
        assertFalse(TapAnswer.cutsSpeech(drillRunning = false, chatPaused = true, silentSession = true))
    }
}
