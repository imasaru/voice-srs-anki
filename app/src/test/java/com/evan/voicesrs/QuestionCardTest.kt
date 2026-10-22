package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionCardTest {

    private val stem = "A data engineer uses higher-order functions with an array column. " +
        "Which function filters array elements based on a lambda condition?"

    private val mcq = """
        $stem
        A. array_contains(arr, value)
        B. filter(arr, x -> x > 100)
        C. transform(arr, x -> x * 2)
        D. aggregate(arr, 0, (acc, x) -> acc + x)
    """.trimIndent()

    @Test
    fun `option lines A through F become monospace rows and the revealed answer is marked`() {
        val parsed = QuestionCard.parse(mcq)
        assertTrue(parsed.mcq)
        assertEquals(stem, parsed.stem)
        assertEquals(listOf("A", "B", "C", "D"), parsed.options.map { it.letter })
        assertEquals("filter(arr, x -> x > 100)", parsed.options[1].text)
        assertTrue(parsed.options.none { it.correct })
        assertEquals("QUESTION 24", QuestionCard.label(24))

        val paren = QuestionCard.parse("Stem line\nA) one\nF) six")
        assertTrue(paren.mcq)
        assertEquals(listOf("A", "F"), paren.options.map { it.letter })
        assertEquals("one", paren.options[0].text)
        assertEquals("six", paren.options[1].text)

        val hidden = QuestionCard.withCorrect(parsed, "B", revealed = false)
        assertTrue(hidden.options.none { it.correct })
        assertFalse(QuestionCard.optionRow(hidden.options[1]).contains("✓"))

        val shown = QuestionCard.withCorrect(parsed, "B. filter(arr, x -> x > 100)", revealed = true)
        assertEquals("B", shown.options.single { it.correct }.letter)
        assertEquals("B. filter(arr, x -> x > 100) ✓", QuestionCard.optionRow(shown.options[1]))
        assertEquals("A. array_contains(arr, value)", QuestionCard.optionRow(shown.options[0]))

        val named = QuestionCard.withCorrect(parsed, "The answer is B", revealed = true)
        assertEquals("B", named.options.single { it.correct }.letter)

        val byText = QuestionCard.withCorrect(
            parsed,
            "filter(arr, x -> x > 100)",
            revealed = true
        )
        assertEquals("B", byText.options.single { it.correct }.letter)

        val ambiguous = QuestionCard.withCorrect(
            parsed,
            "array_contains(arr, value) and filter(arr, x -> x > 100)",
            revealed = true
        )
        assertTrue(ambiguous.options.none { it.correct })

        val withG = QuestionCard.parse("$mcq\nG. not an option")
        assertEquals(4, withG.options.size)
        assertTrue(withG.stem.contains("G. not an option"))
    }

    @Test
    fun `non multiple choice text stays plain and the answer pills are quoted`() {
        val plain = QuestionCard.parse("What is Delta Lake?\nAn ACID storage layer.")
        assertFalse(plain.mcq)
        assertTrue(plain.options.isEmpty())
        assertTrue(plain.stem.contains("What is Delta Lake?"))
        assertTrue(plain.stem.contains("An ACID storage layer."))

        val one = QuestionCard.parse("Only a stem\nA. just one line")
        assertFalse(one.mcq)
        assertTrue(one.options.isEmpty())

        assertEquals("YOUR ANSWER · RATING", QuestionCard.ANSWER_RATING)
        val (quote, answered) = QuestionCard.quotedAnswer("no idea")
        assertEquals("\"no idea\"", quote)
        assertTrue(answered)
        val (blank, said) = QuestionCard.quotedAnswer("   ")
        assertEquals(BubbleModel.NOT_ANSWERED, blank)
        assertFalse(said)
        val (letter, _) = QuestionCard.quotedAnswer("b")
        assertEquals("\"B\"", letter)
    }
}
