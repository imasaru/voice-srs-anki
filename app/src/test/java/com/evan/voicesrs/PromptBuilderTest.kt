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

    @Test
    fun `general chat names study tools and drill prompts do not`() {
        val general = PromptBuilder.generalSystem(true)
        assertTrue(general.contains("get_study_summary"))
        assertTrue(general.contains("get_category_stats"))
        assertTrue(general.contains("get_missed_cards"))
        assertFalse(PromptBuilder.SYSTEM.contains("get_study_summary"))
        assertFalse(PromptBuilder.hintSystem().contains("get_study_summary"))
        assertFalse(PromptBuilder.followUpSystem().contains("get_study_summary"))
        val withData = PromptBuilder.generalMessages("Deck", "How did I do?", "{\"cards\":1}")
        assertTrue(withData.single().content.contains("Study data:"))
        assertTrue(withData.single().content.contains("{\"cards\":1}"))
    }

    @Test
    fun `generalSystem gates the tool call on toolsAvailable`() {
        val withTools = PromptBuilder.generalSystem(true)
        val withoutTools = PromptBuilder.generalSystem(false)
        assertTrue(withTools.contains("get_study_summary"))
        assertTrue(withTools.contains("get_category_stats"))
        assertTrue(withTools.contains("get_missed_cards"))
        assertFalse(withoutTools.contains("get_study_summary"))
        assertFalse(withoutTools.contains("get_category_stats"))
        assertFalse(withoutTools.contains("get_missed_cards"))
        // #55: the fallback carries no data, so it must not claim data is below.
        assertFalse(withoutTools.contains("provided below"))
        assertTrue(withoutTools.contains("No study data is included"))
        assertTrue(withoutTools.contains("Do not call tools."))
        // The injected-data prompt keeps the base tutor framing.
        assertTrue(withoutTools.contains("There is no specific card."))
    }

    @Test
    fun `stripLeftover removes a leftover tool block and keeps the rest`() {
        val wrapped =
            "\u003ctool_call\u003e\u003cfunction=get_study_summary\u003e\u003cparameter=deck\u003eLeaf\u003e\u003c/function\u003e\u003c/tool_call\u003e\n\nThe answer is here."
        val cleaned = TextToolCalls.stripLeftover(wrapped)
        assertFalse(cleaned.contains("tool_call"))
        assertFalse(cleaned.contains("get_study_summary"))
        assertTrue(cleaned.contains("The answer is here."))
    }

    @Test
    fun `reportSystem asks for verbatim report plus one takeaway per group`() {
        val s = PromptBuilder.reportSystem()
        assertTrue(s.contains("Reproduce it in full"))
        assertTrue(s.contains("Takeaway: "))
        assertTrue(s.contains("Do not call tools."))
        assertFalse(s.contains("120 words"))
    }
}
