package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionLabelTest {

    @Test
    fun `a card number becomes the label and leaves the stem`() {
        val question = QuestionLabel.resolve("Question 4\nWhat is a lakehouse?", 9)
        assertEquals("QUESTION 4", question.label)
        assertEquals("What is a lakehouse?", question.stem)
        assertEquals("What is a lakehouse?", question.spoken)
        assertTrue(question.fromCard)

        val q = QuestionLabel.resolve("Q4: What is Delta?", 2)
        assertEquals("QUESTION 4", q.label)
        assertEquals("What is Delta?", q.stem)

        val dotted = QuestionLabel.resolve("Q.4 What is Delta?", 2)
        assertEquals("QUESTION 4", dotted.label)
        assertEquals("What is Delta?", dotted.stem)

        val leadDot = QuestionLabel.resolve("4. What is Delta?\nA. one\nB. two", 8)
        assertEquals("QUESTION 4", leadDot.label)
        assertEquals("What is Delta?\nA. one\nB. two", leadDot.stem)

        val leadParen = QuestionLabel.resolve("4) What is Delta?", 8)
        assertEquals("QUESTION 4", leadParen.label)
        assertEquals("What is Delta?", leadParen.stem)
    }

    @Test
    fun `no card number uses the session count as CARD N`() {
        val face = QuestionLabel.resolve("What is Delta Lake?", 3)
        assertEquals("CARD 3", face.label)
        assertEquals("What is Delta Lake?", face.stem)
        assertEquals("What is Delta Lake?", face.spoken)
        assertFalse(face.fromCard)
        assertEquals("CARD 1", QuestionLabel.resolve("Stem", 1).label)
    }

    @Test
    fun `decimals versions and prose are not question numbers`() {
        val size = QuestionLabel.resolve("4.5 GB is the default cluster size.", 6)
        assertEquals("CARD 6", size.label)
        assertEquals("4.5 GB is the default cluster size.", size.stem)
        assertFalse(size.fromCard)

        val version = QuestionLabel.resolve("Version 3.4.1 ships in March.", 2)
        assertEquals("CARD 2", version.label)
        assertEquals("Version 3.4.1 ships in March.", version.stem)

        val ie = QuestionLabel.resolve("i.e. the clause is not a number.", 5)
        assertEquals("CARD 5", ie.label)
        assertFalse(ie.fromCard)

        val mid = QuestionLabel.resolve("See Question 4 in the notes.", 5)
        assertEquals("CARD 5", mid.label)
        assertTrue(mid.stem.contains("Question 4"))

        val qDecimal = QuestionLabel.resolve("Q4.5 is a build id", 1)
        assertEquals("CARD 1", qDecimal.label)
        assertEquals("Q4.5 is a build id", qDecimal.stem)
    }

    @Test
    fun `asking about the thread and the prompt share the label and the hash does not move`() {
        val html = "<p>Question 4</p><p>What is Delta?</p>"
        val plain = AnkiText.stripHtml(html)
        val face = QuestionLabel.resolve(plain, 3)
        assertEquals("QUESTION 4", face.label)
        assertFalse(face.stem.contains("Question 4"))
        assertEquals(
            "Asking about: QUESTION 4 · Leaf",
            ChatAskLabel.line(face.label, "Leaf")
        )
        assertEquals(
            "Asking about: CARD 3 · Leaf",
            ChatAskLabel.line(QuestionLabel.resolve("What is Delta?", 3).label, "Leaf")
        )
        val hash = ContentHash.of(PromptBuilder.PROMPT_VERSION, html, "B")
        assertEquals(hash, ContentHash.of(PromptBuilder.PROMPT_VERSION, html, "B"))
        assertFalse(face.spoken.contains("Question 4"))
        val prompted = PromptBuilder.userMessage(html, "B", face.label)
        assertTrue(prompted.startsWith("Card: QUESTION 4\n"))
        assertTrue(prompted.contains("Card front:"))
        assertEquals(
            "Card front: ${AnkiText.stripHtml(html)}\nCard back: B",
            PromptBuilder.userMessage(html, "B")
        )
        assertEquals(hash, ContentHash.of(PromptBuilder.PROMPT_VERSION, html, "B"))
    }
}