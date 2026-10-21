package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun `system prompt is the version 1 golden string`() {
        assertEquals(1, PromptBuilder.PROMPT_VERSION)
        assertEquals(
            "You are a concise tutor for the Databricks Certified Data Engineer Associate exam. " +
                "Explain flashcards in ≤120 words: (1) the core concept in one sentence, " +
                "(2) why the answer is correct, (3) one common exam trap or distractor, " +
                "(4) a tiny example (SQL/PySpark/CLI) only if it clarifies. " +
                "Use plain text, no headings or preamble. If the card's answer looks outdated " +
                "or wrong relative to current Databricks docs, say so in one line starting \"Note:\".",
            PromptBuilder.SYSTEM
        )
    }

    @Test
    fun `user message is front and back after stripHtml`() {
        assertEquals(
            "Card front: Hello\nCard back: World",
            PromptBuilder.userMessage("<b>Hello</b>", "World")
        )
        assertFalse(PromptBuilder.userMessage("Q", "the spoken answer").contains("said"))
    }
}
