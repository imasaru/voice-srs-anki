package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** DrillService speaks AnkiText.sanitizeForSpeech(text); the bubble shows the unsanitized text. */
class ReadingSpeechMatchTest {
    @Test
    fun `a plain sentence still matches after sanitize`() {
        val shown = "Masking hides a column.  It runs at query time."
        val spoken = AnkiText.sanitizeForSpeech(shown)
        val parts = SentenceReading.parts(spoken)
        for (p in parts) assertNotNull(p, ReadingSpan.pick(listOf(shown), p))
    }

    @Test
    fun `b gap - a blank marker sentence does not match the shown text`() {
        // KNOWN GAP (h80, not fixed here; sanitize is unchanged): "___" is spoken as "blank".
        val shown = "The ___ policy hides a column."
        val spoken = AnkiText.sanitizeForSpeech(shown)
        assertEquals("The blank policy hides a column.", spoken)
        assertNull(ReadingSpan.pick(listOf(shown), spoken))
    }

    @Test
    fun `c gap - a collapsed symbol run does not match the shown text`() {
        // KNOWN GAP (h80): "----" is spoken as a space, so the words around it no longer line up.
        val shown = "Step one----step two."
        val spoken = AnkiText.sanitizeForSpeech(shown)
        assertNull(ReadingSpan.pick(listOf(shown), spoken))
    }
}
