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
        assertEquals("CARD 24", QuestionCard.label(24))

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

    @Test
    fun `Mock Exam 1 Q20 green is B union not A join even when the back repeats the stem`() {
        val parsed = QuestionCard.parse(q20)
        assertEquals(listOf("A", "B", "C", "D"), parsed.options.map { it.letter })
        assertEquals("df1.join(how=full)", parsed.options[0].text)
        assertEquals("df1.union(df2)", parsed.options[1].text)
        assertEquals("B", QuestionCard.correctLetter("B", parsed.options))
        assertEquals("B", QuestionCard.correctLetter("B. df1.union(df2)", parsed.options))
        assertEquals("B", QuestionCard.highlightSource("B", q20Back))
        assertEquals(q20Back.trim(), QuestionCard.highlightSource("", q20Back))
        assertEquals("B", QuestionCard.correctLetter(q20Back, parsed.options))
        val shown = QuestionCard.withCorrect(parsed, q20Back, revealed = true)
        assertEquals("B", shown.options.single { it.correct }.letter)
        assertEquals("df1.union(df2)", shown.options.single { it.correct }.text)
        assertFalse(shown.options[0].correct)
    }

    @Test
    fun `Q021 Correct field B is green when the stem starts with A PIVOT`() {
        val parsed = QuestionCard.parse(q021)
        assertEquals("B", QuestionCard.correctLetter("B", parsed.options))
        assertEquals("B", QuestionCard.correctLetter(q021Back, parsed.options))
        val shown = QuestionCard.present(parsed, q021Back, revealed = true, chosen = "A")
        assertEquals("B", shown.options.single { it.correct }.letter)
        assertEquals("PIVOT", shown.options.single { it.correct }.text)
        assertTrue(shown.options[0].chosen)
        assertFalse(shown.options[0].correct)
    }

    @Test
    fun `Q025 Correct field C is green when the explanation starts with A common trap`() {
        val parsed = QuestionCard.parse(q025)
        assertEquals("C", QuestionCard.correctLetter("C", parsed.options))
        assertEquals("C", QuestionCard.correctLetter(q025Back, parsed.options))
        val shown = QuestionCard.withCorrect(parsed, q025Back, revealed = true)
        assertEquals("C", shown.options.single { it.correct }.letter)
        assertEquals("OPTIMIZE", shown.options.single { it.correct }.text)
    }

    @Test
    fun `Mock Exam 1 Q43 Correct field B is green when the stem starts with A company`() {
        val parsed = QuestionCard.parse(q043)
        assertEquals(listOf("A", "B", "C", "D"), parsed.options.map { it.letter })
        assertEquals("B", QuestionCard.correctLetter("B", parsed.options))
        assertEquals("B", QuestionCard.highlightSource("B", q043Back))
        assertEquals("B", QuestionCard.correctLetter(q043Back, parsed.options))
        val shown = QuestionCard.present(parsed, "B", revealed = true, chosen = "A")
        assertEquals("B", shown.options.single { it.correct }.letter)
        assertTrue(shown.options.single { it.correct }.text.startsWith("Delta Sharing"))
        assertTrue(shown.options[0].chosen)
        assertFalse(shown.options[0].correct)
    }

    @Test
    fun `scramble keeps green on the original Correct letter not the displayed slot`() {
        val parsed = QuestionCard.parse(q20)
        val session = sessionThatMoves(parsed.options)
        val shuffled = parsed.copy(
            options = OptionShuffle.order(parsed.options, cardId = 20L, sessionStartMs = session, enabled = true)
        )
        assertTrue(shuffled.options.map { it.letter } != listOf("A", "B", "C", "D"))
        val shown = QuestionCard.present(shuffled, "B", revealed = true, chosen = null)
        val green = shown.options.single { it.correct }
        assertEquals("B", green.letter)
        assertEquals("df1.union(df2)", green.text)
        assertTrue(shown.options.indexOf(green) != 1 || shuffled.options[1].letter == "B")
        val fromBack = QuestionCard.present(shuffled, q20Back, revealed = true, chosen = null)
        assertEquals("B", fromBack.options.single { it.correct }.letter)
        assertEquals("df1.union(df2)", fromBack.options.single { it.correct }.text)
        if (fromBack.options[0].letter != "B") {
            assertFalse(fromBack.options[0].correct)
        }
    }

    companion object {
        private val q20 = """
            A Spark DataFrame df1 has rows that also appear in df2. Which call keeps every row from both without a join key?
            A. df1.join(how=full)
            B. df1.union(df2)
            C. df1.intersect(df2)
            D. df1.except(df2)
        """.trimIndent()

        private val q20Back = """
            $q20
            Answer B: df1.union(df2)
            join(how=full) needs a key and pads missing matches. union stacks rows from both DataFrames.
        """.trimIndent()

        private val q021 = """
            A PIVOT query in Databricks SQL rotates which values into columns?
            A. UNPIVOT
            B. PIVOT
            C. STACK
            D. MELT
        """.trimIndent()

        private val q021Back = """
            $q021
            Answer B: PIVOT
            PIVOT rotates row values into columns. UNPIVOT does the reverse.
        """.trimIndent()

        private val q043 = """
            A company wants to share a Delta table with an external partner organization that uses a different cloud provider, without copying the underlying data. Which Databricks feature enables this?
            A. External Location with cross-account IAM role
            B. Delta Sharing — an open protocol for sharing live data across organizations
            C. Unity Catalog data lineage export
            D. DBFS mount with public access enabled
        """.trimIndent()

        private val q043Back = """
            $q043
            Answer B: Delta Sharing — an open protocol for sharing live data across organizations
            Delta Sharing is an open, REST-based protocol for sharing live Delta Lake data without copying it. External Locations manage storage access within one organization.
        """.trimIndent()

        private val q025 = """
            A Delta table has many small files. Which command rewrites them into larger files?
            A. VACUUM
            B. DESCRIBE HISTORY
            C. OPTIMIZE
            D. RESTORE
        """.trimIndent()

        private val q025Back = """
            A common trap is to VACUUM first.
            Answer C: OPTIMIZE
            OPTIMIZE compacts small files. VACUUM removes unreferenced files after a retention period.
        """.trimIndent()

        private fun sessionThatMoves(options: List<QuestionCard.Option>): Long {
            var session = 1L
            while (session < 200L) {
                val letters = OptionShuffle.order(options, 20L, session, enabled = true).map { it.letter }
                if (letters != options.map { it.letter }) return session
                session++
            }
            throw AssertionError("expected some session seed to shuffle the options")
        }
    }
}
