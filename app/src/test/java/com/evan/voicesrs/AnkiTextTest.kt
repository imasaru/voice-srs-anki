package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnkiTextTest {
    private val kengio: JSONObject by lazy {
        val stream = checkNotNull(javaClass.classLoader).getResourceAsStream("kengio-samples.json")
        JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun kengioField(noteType: String, field: String): String =
        kengio.getJSONObject(noteType).getString(field)

    private val mcqStem =
        "A data engineering team is evaluating whether to migrate their existing data warehouse to a Databricks Lakehouse. The primary concern is maintaining ACID guarantees for concurrent reads and writes on large tables. Which Databricks technology directly addresses this requirement?"
    @Test
    fun `strips simple tags`() {
        assertEquals("What is the lakehouse ?", AnkiText.stripHtml("<div>What is the <b>lakehouse</b>?</div>"))
    }

    @Test
    fun `br and block tags become spaces`() {
        val out = AnkiText.stripHtml("line one<br>line two<p>para</p>")
        assertTrue(out.contains("line one"))
        assertTrue(out.contains("line two"))
        assertTrue(out.contains("para"))
        assertFalse(out.contains("<"))
    }

    @Test
    fun `decodes entities`() {
        assertEquals("fish & chips", AnkiText.stripHtml("fish &amp; chips"))
        assertEquals("a < b", AnkiText.stripHtml("a &lt; b"))
        assertEquals("don't", AnkiText.stripHtml("don&rsquo;t"))
        assertEquals("A", AnkiText.stripHtml("&#65;"))
    }

    @Test
    fun `removes script and style`() {
        assertEquals("visible", AnkiText.stripHtml("<style>.x{}</style>visible<script>alert(1)</script>"))
    }

    @Test
    fun `collapses whitespace and trims`() {
        assertEquals("a b c", AnkiText.stripHtml("  a\n\n  b\t\tc  "))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", AnkiText.stripHtml(""))
    }

    @Test
    fun `display keeps bold and drops the wrapping paragraph`() {
        assertEquals(
            "What does the <b>bronze</b> layer hold?",
            AnkiText.toDisplayHtml("<p>What does the <b>bronze</b> layer hold?</p>")
        )
    }

    @Test
    fun `display keeps line breaks from br and blocks`() {
        assertEquals(
            "line one<br>line two<br>para",
            AnkiText.toDisplayHtml("line one<br>line two<p>para</p>")
        )
    }

    @Test
    fun `display keeps italic and strong`() {
        assertEquals(
            "<strong>OPTIMIZE</strong> then <i>ZORDER</i> and <em>merge</em>",
            AnkiText.toDisplayHtml("<strong>OPTIMIZE</strong> then <i>ZORDER</i> and <em>merge</em>")
        )
    }

    @Test
    fun `display drops images sound media scripts and styles`() {
        val raw = "<style>.x{}</style><img src=\"x.png\">[sound:a.mp3]<script>alert(1)</script>Hello"
        assertEquals("Hello", AnkiText.toDisplayHtml(raw))
        assertFalse(AnkiText.toDisplayHtml(raw).contains("sound"))
        assertFalse(AnkiText.toDisplayHtml(raw).contains("img"))
    }

    @Test
    fun `display decodes entities without keeping raw tags`() {
        assertEquals("fish &amp; chips", AnkiText.toDisplayHtml("fish &amp; chips"))
        assertEquals("a &lt; b", AnkiText.toDisplayHtml("a &lt; b"))
    }

    @Test
    fun `display splits divs onto lines`() {
        assertEquals("one<br>two", AnkiText.toDisplayHtml("<div>one</div><div>two</div>"))
    }

    @Test
    fun `answerOnly drops the front side before hr id=answer`() {
        assertEquals(
            "A storage layer.",
            AnkiText.answerOnly("What is the lakehouse?<hr id=answer>A storage layer.")
        )
    }

    @Test
    fun `answerOnly accepts a single quoted answer id`() {
        assertEquals(
            "A storage layer.",
            AnkiText.answerOnly("What is the lakehouse?<hr id='answer'>A storage layer.")
        )
    }

    @Test
    fun `answerOnly accepts an uppercase answer divider with a class`() {
        assertEquals(
            "A storage layer.",
            AnkiText.answerOnly("What is the lakehouse?<HR ID=\"answer\" class=x>A storage layer.")
        )
    }

    @Test
    fun `answerOnly keeps html that has no answer marker`() {
        val html = "Question<hr>Answer"
        assertEquals(html, AnkiText.answerOnly(html))
    }

    @Test
    fun `answerOnly keeps the full html when nothing follows the marker`() {
        val html = "<div>Question</div><hr id=answer><br>  "
        assertEquals(html, AnkiText.answerOnly(html))
    }

    @Test
    fun `cards without hidden markup match the old stripHtml and display html`() {
        val samples = listOf(
            "<div>What is the <b>lakehouse</b>?</div>",
            "line one<br>line two<p>para</p>",
            "fish &amp; chips",
            "a &lt; b",
            "don&rsquo;t",
            "&#65;",
            "<style>.x{}</style>visible<script>alert(1)</script>",
            "  a\n\n  b\t\tc  ",
            "",
            "<p>What does the <b>bronze</b> layer hold?</p>",
            "<strong>OPTIMIZE</strong> then <i>ZORDER</i> and <em>merge</em>",
            "<style>.x{}</style><img src=\"x.png\">[sound:a.mp3]<script>alert(1)</script>Hello",
            "<div>one</div><div>two</div>",
            "What is the lakehouse?<hr id=answer>A storage layer.",
            "display:none is a word <b>Visible</b>",
            """<div class="bespoken">Keep me</div>""",
            """<div title="class=spoken" style="color:red">Visible</div>""",
            """<!-- <div class=spoken>Nope</div> --><div>Yes</div>""",
            kengioField("Basic (kengio)", "q"),
            kengioField("Basic (kengio)", "a")
        )
        for (html in samples) {
            assertEquals(AnkiText.stripHtml(html), AnkiText.spokenText(html))
            assertEquals(AnkiText.toDisplayHtml(html), AnkiText.displayHtml(html))
        }
        val basicAnswer = AnkiText.answerOnly(kengioField("Basic (kengio)", "a"))
        assertEquals(AnkiText.stripHtml(basicAnswer), AnkiText.spokenText(basicAnswer))
        assertEquals(AnkiText.toDisplayHtml(basicAnswer), AnkiText.displayHtml(basicAnswer))
        assertTrue(AnkiText.spokenText(basicAnswer).contains("Parquet"))
        assertTrue(AnkiText.spokenText(basicAnswer).contains("Source:"))
    }

    @Test
    fun `mcq question speaks only the spoken stem`() {
        val q = kengioField("Voice MCQ (kengio)", "q")
        val spoken = AnkiText.spokenText(q)
        assertEquals(mcqStem, spoken)
        assertFalse(spoken.contains("Question 1"))
        assertFalse(spoken.contains("Delta Lake"))
        assertFalse(spoken.contains("Apache Parquet"))
        assertFalse(spoken.contains("Unity Catalog"))
    }

    @Test
    fun `mcq answer after the divider speaks the choice summary and source without the explanation`() {
        val answer = AnkiText.spokenText(AnkiText.answerOnly(kengioField("Voice MCQ (kengio)", "a")))
        assertEquals(
            "Answer B: Delta Lake with transaction log ( _delta_log ) Delta Lake provides ACID " +
                "transactions through its transaction log (_delta_log), which records every operation " +
                "as an atomic commit. This enables concurrent readers and writers without data corruption. " +
                "Source: https://github.com/kengio/databricks-certification-study-guide @78104bb " +
                "License: MIT © 2026 Suppaseth Charoenkarnka (kengio)",
            answer
        )
        assertFalse(answer.contains("Parquet alone"))
        assertFalse(answer.contains("governance"))
        assertFalse(answer.contains("Question 1"))
        assertFalse(answer.contains(mcqStem))
        assertFalse(answer.contains("<"))
    }

    @Test
    fun `display hides the spoken duplicate and keeps the screen-only explanation`() {
        val q = kengioField("Voice MCQ (kengio)", "q")
        val a = kengioField("Voice MCQ (kengio)", "a")
        assertEquals(2, AnkiText.toDisplayHtml(q).split(mcqStem).size - 1)
        assertEquals(1, AnkiText.displayHtml(q).split(mcqStem).size - 1)
        assertTrue(AnkiText.displayHtml(q).contains("Question 1"))
        assertTrue(AnkiText.displayHtml(q).contains("Apache Parquet"))

        val full = AnkiText.displayHtml(a)
        assertEquals(1, full.split(mcqStem).size - 1)
        assertTrue(full.contains("Answer B:"))
        assertTrue(full.contains("Parquet alone has no transaction support"))
        assertTrue(full.contains("not storage-level ACID"))

        val back = AnkiText.displayHtml(AnkiText.answerOnly(a))
        assertTrue(back.contains("Answer B:"))
        assertTrue(back.contains("Parquet alone has no transaction support"))
        assertTrue(back.contains("Source:"))
        assertFalse(back.contains("Question 1"))
        assertFalse(back.contains(mcqStem))
    }

    @Test
    fun `spoken class accepts quotes and extra classes`() {
        val variants = listOf(
            """<div class="spoken">Say this</div><p>Shown</p>""",
            """<div class='spoken'>Say this</div><p>Shown</p>""",
            """<div class=spoken>Say this</div><p>Shown</p>""",
            """<div class="x spoken">Say this</div><p>Shown</p>""",
            """<div class='spoken extra'>Say this</div><p>Shown</p>""",
            """<span class="foo spoken bar">Say this</span><div>Shown</div>""",
            """<DIV CLASS="spoken">Say this</DIV><div>Shown</div>""",
            "<div\n class=\"spoken\">Say this</div><div>Shown</div>"
        )
        for (html in variants) {
            assertEquals(html, "Say this", AnkiText.spokenText(html))
            val shown = AnkiText.displayHtml(html)
            assertFalse(shown.contains("Say this"))
            assertTrue(shown.contains("Shown"))
        }
        assertEquals(
            "One Two",
            AnkiText.spokenText("""<div class=spoken>One</div><div>no</div><div class="spoken">Two</div>""")
        )
    }

    @Test
    fun `screen-only with a nested div is silent and still displayed`() {
        val html = """<div>Keep</div><div class="screen-only"><div>nested <b>secret</b></div><p>more</p></div>"""
        assertEquals("Keep", AnkiText.spokenText(html))
        assertEquals(AnkiText.toDisplayHtml(html), AnkiText.displayHtml(html))
        val shown = AnkiText.displayHtml(html)
        assertTrue(shown.contains("Keep"))
        assertTrue(shown.contains("nested"))
        assertTrue(shown.contains("<b>secret</b>"))
        assertTrue(shown.contains("more"))

        assertEquals(
            "Keep",
            AnkiText.spokenText("""<div>Keep</div><div class='screen-only'><div><div>deep</div></div></div>""")
        )
        assertEquals(
            "Keep",
            AnkiText.spokenText("""<div>Keep</div><div class=screen-only><div>deep</div></div>""")
        )
        assertEquals(
            "Keep",
            AnkiText.spokenText("""<div>Keep</div><div class="note screen-only"><div>deep</div></div>""")
        )
    }

    @Test
    fun `inline display none is dropped unless the element is the spoken replacement`() {
        val hidden = listOf(
            """<div style="display:none">Hidden</div><div>Visible</div>""",
            """<div style='display: none'>Hidden</div><span>Visible</span>""",
            """<p style=display:none>Hidden</p><p>Visible</p>""",
            """<div style="color:red; display:none">Hidden</div>Visible""",
            """<div style="display:none !important">Hidden</div>Visible""",
            """<div STYLE="DISPLAY: none">Hidden</div><div>Visible</div>""",
            """<div style="display:none">Hidden <div>still</div></div><div>Visible</div>"""
        )
        for (html in hidden) {
            assertEquals(html, "Visible", AnkiText.spokenText(html))
            assertEquals(html, "Visible", AnkiText.displayHtml(html))
            assertFalse(AnkiText.spokenText(html).contains("Hidden"))
            assertFalse(AnkiText.displayHtml(html).contains("Hidden"))
        }
        val replacement = """<div class="spoken" style="display:none">Say this</div><div>Visible</div>"""
        assertEquals("Say this", AnkiText.spokenText(replacement))
        assertEquals("Visible", AnkiText.displayHtml(replacement))
        assertEquals(
            "Say this",
            AnkiText.spokenText(
                """<div class=spoken>Say <span style="display:none">no</span><div class=screen-only><div>nope</div></div>this</div>"""
            )
        )
    }

    @Test
    fun `answer side without spoken uses the answer normally and ignores the front spoken element`() {
        val html = """<div class=spoken>Question stem</div><div>Front</div><hr id=answer><div>The answer</div><div class=screen-only><div>Explain <b>why</b></div></div>"""
        val body = AnkiText.answerOnly(html)
        assertEquals("The answer", AnkiText.spokenText(body))
        val shown = AnkiText.displayHtml(body)
        assertTrue(shown.contains("The answer"))
        assertTrue(shown.contains("Explain"))
        assertTrue(shown.contains("why"))
        assertFalse(shown.contains("Question stem"))
    }
}
