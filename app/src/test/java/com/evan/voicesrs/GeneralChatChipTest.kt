package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralChatChipTest {
    /** [usesReportPath] is true only for the built-in Today's report chip at its defaults. */
    @Test
    fun `usesReportPath true for the built-in default`() {
        assertTrue(GeneralChat.usesReportPath(PromptChips.defaults()[0]))
    }

    @Test
    fun `usesReportPath false when the prompt is edited`() {
        val chip = PromptChip(
            PromptChips.REPORT_ID,
            PromptChips.REPORT_NAME,
            "Quiz me on today's misses",
            ChipSource.TODAY_REVIEWS,
            builtIn = true
        )
        assertFalse(GeneralChat.usesReportPath(chip))
    }

    @Test
    fun `usesReportPath false when the source is edited`() {
        val chip = PromptChip(
            PromptChips.REPORT_ID,
            PromptChips.REPORT_NAME,
            PromptChips.REPORT_PROMPT,
            ChipSource.CURRENT_CARD,
            builtIn = true
        )
        assertFalse(GeneralChat.usesReportPath(chip))
    }

    @Test
    fun `usesReportPath false when the id is not the built-in report id`() {
        val chip = PromptChip("quiz", "Quiz me", PromptChips.REPORT_PROMPT, ChipSource.TODAY_REVIEWS)
        assertFalse(GeneralChat.usesReportPath(chip))
    }

    @Test
    fun `usesReportPath ignores surrounding whitespace`() {
        val chip = PromptChip(
            PromptChips.REPORT_ID,
            PromptChips.REPORT_NAME,
            "  " + PromptChips.REPORT_PROMPT + "\n",
            ChipSource.TODAY_REVIEWS,
            builtIn = true
        )
        assertTrue(GeneralChat.usesReportPath(chip))
    }
}
