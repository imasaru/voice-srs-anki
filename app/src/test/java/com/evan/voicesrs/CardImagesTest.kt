package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---------- Forced-tap image-wait voice fallback ----------

    @Test
    fun `wait fallback speaks a prompt when the policy speaks`() {
        val fb = CardImages.waitFallback(
            CardImages.policy("<img src=\"a.png\">Sign?"),
            autoAdvance = false
        )
        assertEquals("Say next or continue to move on.", fb.prompt)
    }

    @Test
    fun `wait fallback has no prompt when the policy does not speak`() {
        val fb = CardImages.waitFallback(
            CardImages.Policy(speakPrompt = false, autoAdvance = false, voiceGrade = false),
            autoAdvance = false
        )
        assertNull(fb.prompt)
    }

    @Test
    fun `wait fallback resumes on next or continue, case-insensitive`() {
        val fb = CardImages.waitFallback(
            CardImages.Policy(speakPrompt = true, autoAdvance = false, voiceGrade = false),
            autoAdvance = false
        )
        assertTrue(fb.resumes("next"))
        assertTrue(fb.resumes("continue"))
        assertTrue(fb.resumes("  NEXT  "))
        assertTrue(fb.resumes("Continue"))
        assertFalse(fb.resumes(""))
        assertFalse(fb.resumes(null))
        assertFalse(fb.resumes("good"))
        assertFalse(fb.resumes("again"))
    }

    @Test
    fun `wait fallback auto-advances only with a positive timeout`() {
        val on = CardImages.waitFallback(
            CardImages.Policy(speakPrompt = true, autoAdvance = true, voiceGrade = false),
            autoAdvance = true, timeoutMs = 30_000L
        )
        assertEquals(30_000L, on.autoAdvanceTimeoutMs)

        val off = CardImages.waitFallback(
            CardImages.Policy(speakPrompt = true, autoAdvance = true, voiceGrade = false),
            autoAdvance = false, timeoutMs = 30_000L
        )
        assertEquals(0L, off.autoAdvanceTimeoutMs)

        val zero = CardImages.waitFallback(
            CardImages.Policy(speakPrompt = true, autoAdvance = true, voiceGrade = false),
            autoAdvance = true, timeoutMs = 0L
        )
        assertEquals(0L, zero.autoAdvanceTimeoutMs)
    }
}
