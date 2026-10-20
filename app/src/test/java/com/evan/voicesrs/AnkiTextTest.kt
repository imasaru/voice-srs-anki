package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

class AnkiTextTest {
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
}
