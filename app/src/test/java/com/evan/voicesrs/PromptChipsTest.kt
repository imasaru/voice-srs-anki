package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptChipsTest {

    @Test
    fun `defaults is the single built-in report chip`() {
        val d = PromptChips.defaults()
        assertEquals(1, d.size)
        val c = d[0]
        assertEquals(PromptChips.REPORT_ID, c.id)
        assertEquals(PromptChips.REPORT_NAME, c.name)
        assertEquals(PromptChips.REPORT_PROMPT, c.prompt)
        assertEquals(ChipSource.TODAY_REVIEWS, c.source)
        assertEquals(true, c.builtIn)
    }

    @Test
    fun `resetBuiltIn keeps position and restores the report chip`() {
        val editedReport = PromptChip(
            PromptChips.REPORT_ID,
            "My report",
            "x",
            ChipSource.TODAY_REVIEWS,
            builtIn = false
        )
        val quiz = PromptChip("quiz", "Quiz me on misses", "quiz me on misses", ChipSource.CARD_CHATS)
        val result = PromptChips.resetBuiltIn(listOf(quiz, editedReport))
        assertEquals(2, result.size)
        assertEquals("quiz", result[0].id)
        assertEquals(PromptChips.REPORT_NAME, result[1].name)
        assertEquals(PromptChips.REPORT_PROMPT, result[1].prompt)
        assertEquals(ChipSource.TODAY_REVIEWS, result[1].source)
        assertEquals(true, result[1].builtIn)
    }

    @Test
    fun `resetBuiltIn inserts report first when deleted`() {
        val quiz = PromptChip("quiz", "Quiz me on misses", "quiz me on misses", ChipSource.CARD_CHATS)
        val result = PromptChips.resetBuiltIn(listOf(quiz))
        assertEquals(2, result.size)
        assertEquals(PromptChips.REPORT_ID, result[0].id)
        assertEquals(PromptChips.REPORT_NAME, result[0].name)
        assertEquals("quiz", result[1].id)
    }

    @Test
    fun `resetBuiltIn leaves a non-report chip identical`() {
        val quiz = PromptChip("quiz", "Quiz me on misses", "quiz me on misses", ChipSource.CARD_CHATS)
        val result = PromptChips.resetBuiltIn(listOf(quiz))
        // resetBuiltIn inserts the default report chip; the non-report chip itself is unchanged.
        assertEquals(2, result.size)
        assertEquals(quiz, result.first { it.id == "quiz" })
    }
}
