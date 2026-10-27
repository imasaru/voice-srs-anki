package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `toJson fromJson round-trips a multi-chip list in order`() {
        val chips = listOf(
            PromptChips.defaults()[0],
            PromptChip("quiz", "Quiz me on misses", "quiz me on misses", ChipSource.TODAY_REVIEWS),
            PromptChip("explain", "Explain simply", "Explain this simply.", ChipSource.CARD_CHATS)
        )
        assertEquals(chips, PromptChips.fromJson(PromptChips.toJson(chips)))
    }

    @Test
    fun `toJson fromJson round-trips a prompt with quotes newline and closing tag`() {
        val tricky = PromptChip(
            "t",
            "Tricky",
            "Line1\nLine2 \"quoted\" </study_data>",
            ChipSource.TODAY_REVIEWS
        )
        assertEquals(listOf(tricky), PromptChips.fromJson(PromptChips.toJson(listOf(tricky))))
    }

    @Test
    fun `fromJson falls back to defaults on blank or unparseable text`() {
        assertEquals(PromptChips.defaults(), PromptChips.fromJson(""))
        assertEquals(PromptChips.defaults(), PromptChips.fromJson("   "))
        assertEquals(PromptChips.defaults(), PromptChips.fromJson("not json"))
    }

    @Test
    fun `fromJson of empty array is empty`() {
        assertTrue(PromptChips.fromJson("[]").isEmpty())
    }

    @Test
    fun `fromJson skips entries with unknown source or blank name in order`() {
        val arr = JSONArray()
        arr.put(JSONObject().put("id", "a").put("name", "A").put("prompt", "pa")
            .put("source", "TODAY_REVIEWS").put("builtIn", false))
        arr.put(JSONObject().put("id", "b").put("name", "B").put("prompt", "pb")
            .put("source", "CURRENT_CARD").put("builtIn", false))
        arr.put(JSONObject().put("id", "c").put("name", "C").put("prompt", "pc")
            .put("source", "BOGUS").put("builtIn", false))
        arr.put(JSONObject().put("id", "d").put("name", "").put("prompt", "pd")
            .put("source", "CURRENT_CARD").put("builtIn", false))
        val result = PromptChips.fromJson(arr.toString())
        assertEquals(2, result.size)
        assertEquals("a", result[0].id)
        assertEquals(ChipSource.TODAY_REVIEWS, result[0].source)
        assertEquals("b", result[1].id)
        assertEquals(ChipSource.CURRENT_CARD, result[1].source)
    }

    @Test
    fun `fromJson reads missing builtIn as false`() {
        val arr = JSONArray()
        arr.put(JSONObject().put("id", "x").put("name", "X").put("prompt", "px")
            .put("source", "CURRENT_CARD"))
        val c = PromptChips.fromJson(arr.toString())[0]
        assertFalse(c.builtIn)
    }
}
