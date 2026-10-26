package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardImagesTest {

    @Test
    fun `wait for tap on a nearly silent image and on occlusion`() {
        assertTrue(CardImages.waitForTap("""<img src="a.png">Sign?"""))
        assertTrue(CardImages.waitForTap("""<div id="image-occlusion-container"></div>"""))
        assertTrue(CardImages.policy("""<img src="a.png">Sign?""").speakPrompt)
        assertFalse(CardImages.policy("""<img src="a.png">Sign?""").autoAdvance)
        assertFalse(CardImages.policy("""<img src="a.png">Sign?""").voiceGrade)
    }

    @Test
    fun `a long image card still uses the voice loop`() {
        val html = """<img src="a.png"><p>The lakehouse stores tables in Parquet for efficient reads.</p>"""
        assertFalse(CardImages.waitForTap(html))
        val policy = CardImages.policy(html)
        assertFalse(policy.speakPrompt)
        assertTrue(policy.autoAdvance)
        assertTrue(policy.voiceGrade)
    }

    @Test
    fun `plain text does not wait for a tap`() {
        assertFalse(CardImages.waitForTap("What is Delta?"))
        assertTrue(CardImages.policy("What is Delta?").autoAdvance)
    }

    @Test
    fun `resolved image refs become img tags and misses become a placeholder`() {
        val html = """<p>See <img src="a.png"> and <img src="missing.png"> now</p>"""
        val out = CardImages.displayHtml(html, mapOf("a.png" to "a.png"))
        assertTrue(out.contains("""<img src="a.png">"""))
        assertTrue(out.contains(CardImages.PLACEHOLDER))
        assertFalse(out.contains("missing.png"))
        assertTrue(out.contains("See"))
        assertTrue(out.contains("now"))
    }

    @Test
    fun `percent-encoded refs match the decoded resolved key`() {
        val html = """<img src="my%20card.png">Label"""
        val out = CardImages.displayHtml(html, mapOf("my card.png" to "my card.png"))
        assertTrue(out.contains("""<img src="my card.png">"""))
        assertTrue(out.contains("Label"))
    }

    @Test
    fun `unresolved images become placeholder text`() {
        val out = CardImages.displayHtml("""<img src="a.png">Sign?""", emptyMap())
        assertEquals("${CardImages.PLACEHOLDER}Sign?", out)
        assertFalse(out.contains("<img"))
    }

    @Test
    fun `cards without images match displayHtml`() {
        val html = "<p>What does the <b>bronze</b> layer hold?</p>"
        assertEquals(AnkiText.displayHtml(html), CardImages.displayHtml(html, emptyMap()))
    }
}
