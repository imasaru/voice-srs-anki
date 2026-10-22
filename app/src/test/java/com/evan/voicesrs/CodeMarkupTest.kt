package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeMarkupTest {
    private val front = "<p>Use <code>OPTIMIZE</code> on the table.</p>"
    private val back = "<p>Run <code>OPTIMIZE tbl</code>.</p><pre>OPTIMIZE tbl</pre>"

    @Test
    fun `spoken text and content hash ignore code tags`() {
        assertEquals("Use OPTIMIZE on the table.", AnkiText.stripHtml(front))
        assertEquals("Use OPTIMIZE on the table.", AnkiText.spokenText(front))
        assertEquals("Run OPTIMIZE tbl . OPTIMIZE tbl", AnkiText.stripHtml(back))
        assertEquals("Run OPTIMIZE tbl . OPTIMIZE tbl", AnkiText.spokenText(back))
        val plainFront = "Use OPTIMIZE on the table."
        val plainBack = "Run OPTIMIZE tbl . OPTIMIZE tbl"
        assertEquals(
            ContentHash.of(PromptBuilder.PROMPT_VERSION, plainFront, plainBack),
            ContentHash.of(PromptBuilder.PROMPT_VERSION, front, back)
        )
        assertEquals(
            "33aed9e0979d420408149eeacd96731ef9c49491364e00dfd637f8dd4aa01653",
            ContentHash.of(PromptBuilder.PROMPT_VERSION, front, back)
        )
        assertEquals(
            "Card front: $plainFront\nCard back: $plainBack",
            PromptBuilder.userMessage(front, back)
        )
        assertFalse(PromptBuilder.userMessage(front, back).contains("<"))
    }

    @Test
    fun `stem keeps a code chip and decodes entities inside it`() {
        val html = "<p>Use <code>a &lt; b &amp; c</code> now.</p>"
        val shown = AnkiText.displayHtml(html)
        assertTrue(shown.contains("<code>a &lt; b &amp; c</code>"))
        val face = BubbleModel.fullQuestion(html)
        assertEquals("Use <code>a &lt; b &amp; c</code> now.", face)
        val chip = CodeMarkup.runs(face).single { it.kind == CodeMarkup.Kind.CODE }
        assertEquals("a < b & c", chip.text)
        assertEquals("Use ", CodeMarkup.runs(face).first().text)
        assertEquals(AnkiText.stripHtml(html), AnkiText.stripHtml(face))
        assertEquals("No code here.", AnkiText.displayPlain("<p>No code here.</p>"))
        assertEquals(
            AnkiText.stripHtml("<p>No code here.</p>"),
            AnkiText.displayPlain("<p>No code here.</p>")
        )
    }

    @Test
    fun `explanation keeps inline code and a pre block`() {
        val html = "<p>Run <code>OPTIMIZE tbl</code>.</p><pre>OPTIMIZE tbl\nWHERE x &gt; 1</pre>"
        val shown = AnkiText.displayHtml(html)
        assertTrue(shown.contains("<code>OPTIMIZE tbl</code>"))
        assertTrue(shown.contains("<pre>OPTIMIZE tbl\nWHERE x &gt; 1</pre>"))
        val runs = CodeMarkup.runs(shown)
        assertEquals("OPTIMIZE tbl", runs.single { it.kind == CodeMarkup.Kind.CODE }.text)
        assertEquals("OPTIMIZE tbl\nWHERE x > 1", runs.single { it.kind == CodeMarkup.Kind.PRE }.text)
        val prepared = CodeMarkup.prepareForHtml(shown)
        assertTrue(prepared.contains("<vsrscode>OPTIMIZE tbl</vsrscode>"))
        assertTrue(prepared.contains("<vsrspre>OPTIMIZE tbl<br>WHERE x &gt; 1</vsrspre>"))
        assertFalse(prepared.contains("<code"))
        assertFalse(prepared.contains("<pre"))
    }

    @Test
    fun `mcq option row keeps code and pre is not an option line`() {
        val front = """
            Which log does Delta Lake use?
            A. Apache Parquet
            B. Delta Lake (<code>_delta_log</code>)
            C. CSV files
        """.trimIndent()
        val face = BubbleModel.fullQuestion(front)
        val parsed = QuestionCard.parse(face)
        assertTrue(parsed.mcq)
        assertEquals("Which log does Delta Lake use?", parsed.stem)
        val option = parsed.options.single { it.letter == "B" }
        assertTrue(option.text.contains("<code>_delta_log</code>"))
        val row = CodeMarkup.runs(QuestionCard.optionRow(option))
        assertEquals("_delta_log", row.single { it.kind == CodeMarkup.Kind.CODE }.text)
        assertEquals(
            "B",
            QuestionCard.correctLetter(
                "Delta Lake ( _delta_log ) is the answer",
                parsed.options
            )
        )

        val withPre = "Stem\n<pre>A. not an option\nB. also code</pre>\nA. real one\nB. real two"
        val preParsed = QuestionCard.parse(withPre)
        assertTrue(preParsed.mcq)
        assertEquals(listOf("A", "B"), preParsed.options.map { it.letter })
        assertEquals("real one", preParsed.options[0].text)
        assertTrue(preParsed.stem.contains("<pre>"))
        val again = BubbleModel.fullQuestion(face)
        assertEquals(face, again)
    }
}
