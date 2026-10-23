package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun `system prompt is the version 2 golden string`() {
        assertEquals(2, PromptBuilder.PROMPT_VERSION)
        assertEquals(
            "You are a concise tutor for the Databricks Certified Data Engineer Associate exam. " +
                "Explain flashcards in ≤120 words. Always reply with these headed sections, in order: " +
                "Core concept, Why <letter>, Common traps, Example. " +
                "Core concept is one sentence. Why <letter> names the correct option letter and says why it is right. " +
                "Common traps is a bullet list. Example is a fenced code block. Put code in backticks. " +
                "If the card's answer looks outdated or wrong relative to current Databricks docs, " +
                "end with one line starting \"Note:\". No preamble.",
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

    @Test
    fun `hint mode withholds the answer and the revealed prompt stays version 2`() {
        assertEquals(2, PromptBuilder.PROMPT_VERSION)
        val hint = PromptBuilder.hintSystem()
        assertFalse(hint == PromptBuilder.SYSTEM)
        assertTrue(hint.contains("withholding the answer"))
        assertFalse(hint.contains("Why <letter>"))
        val secret = "CORRECT_OPTION_ZEPHYR"
        val messages = PromptBuilder.hintMessages(
            "What is a view?\nA. one\nB. two",
            emptyList(),
            "is it A?"
        )
        val blob = messages.joinToString("\n") { it.content }
        assertFalse(blob.contains(secret))
        assertFalse(blob.contains("Card back:"))
        assertTrue(blob.contains("The answer is hidden."))
        assertTrue(blob.contains("is it A?"))
        val revealed = PromptBuilder.followUpMessages(
            "Front",
            secret,
            "B",
            "",
            emptyList(),
            "why B?"
        )
        assertTrue(revealed.first().content.contains("Card back: $secret"))
        assertEquals(PromptBuilder.SYSTEM + PromptBuilder.FOLLOW_UP_EXTRA, PromptBuilder.followUpSystem())
    }

    @Test
    fun `follow-up history is role based and never labels a Learner turn`() {
        val prior = listOf(
            FollowUp("why not A?", ExplState.Ready("A is the trap.", cached = false))
        )
        val messages = PromptBuilder.followUpMessages(
            "Front",
            "Back",
            "B",
            "Why B is right.",
            prior,
            "nice llama analogy?"
        )
        val blob = messages.joinToString("\n") { "${it.role}:${it.content}" }
        assertFalse(blob.contains("Learner said:"))
        assertFalse(blob.contains("Learner:"))
        assertEquals(
            listOf("user", "user", "assistant", "user", "assistant", "user"),
            messages.map { it.role }
        )
        assertEquals("B", messages[1].content)
        assertEquals("Why B is right.", messages[2].content)
        assertEquals("why not A?", messages[3].content)
        assertEquals("A is the trap.", messages[4].content)
        assertEquals("nice llama analogy?", messages[5].content)
        assertTrue(messages[0].content.contains("Card front: Front"))
    }
}
