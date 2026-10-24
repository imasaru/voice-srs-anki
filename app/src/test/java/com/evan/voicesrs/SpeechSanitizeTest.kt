package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

class SpeechSanitizeTest {
    @Test
    fun `three or more underscores become the word blank`() {
        assertEquals("The blank is red", AnkiText.sanitizeForSpeech("The ___ is red"))
    }

    @Test
    fun `every run of underscores becomes a blank`() {
        assertEquals("a blank b blank c", AnkiText.sanitizeForSpeech("a ______ b ___ c"))
    }

    @Test
    fun `two underscores are left untouched`() {
        assertEquals("__", AnkiText.sanitizeForSpeech("__"))
    }

    @Test
    fun `a single underscore is left untouched`() {
        assertEquals("a_b", AnkiText.sanitizeForSpeech("a_b"))
    }

    @Test
    fun `a run of equal signs is stripped`() {
        assertEquals("Title Body", AnkiText.sanitizeForSpeech("Title\n=====\nBody"))
        assertFalse(AnkiText.sanitizeForSpeech("Title\n=====\nBody").contains("="))
    }

    @Test
    fun `a run of dashes is stripped`() {
        assertEquals("x y", AnkiText.sanitizeForSpeech("x ---- y"))
    }

    @Test
    fun `runs of other symbols are stripped`() {
        assertEquals("a b", AnkiText.sanitizeForSpeech("a **** b"))
        assertEquals("a b", AnkiText.sanitizeForSpeech("a #### b"))
        assertEquals("a b", AnkiText.sanitizeForSpeech("a ~~~~ b"))
        assertEquals("a b", AnkiText.sanitizeForSpeech("a .... b"))
    }

    @Test
    fun `a normal sentence is unchanged`() {
        assertEquals("The lakehouse stores tables.", AnkiText.sanitizeForSpeech("The lakehouse stores tables."))
        assertEquals("Answer B: Delta Lake with transaction log",
            AnkiText.sanitizeForSpeech("Answer B: Delta Lake with transaction log"))
    }

    @Test
    fun `single punctuation marks are kept`() {
        assertEquals("What? Yes.", AnkiText.sanitizeForSpeech("What? Yes."))
        assertEquals("a, b, c", AnkiText.sanitizeForSpeech("a, b, c"))
    }

    @Test
    fun `an ellipsis of three dots is kept`() {
        assertEquals("Wait...yes", AnkiText.sanitizeForSpeech("Wait...yes"))
    }

    @Test
    fun `a longer run of dots is stripped`() {
        assertEquals("a b", AnkiText.sanitizeForSpeech("a ..... b"))
    }

    @Test
    fun `digits are not treated as symbols`() {
        assertEquals("404", AnkiText.sanitizeForSpeech("404"))
        assertEquals("111 222", AnkiText.sanitizeForSpeech("111 222"))
    }

    @Test
    fun `spoken text runs the sanitizer too`() {
        assertEquals("The blank is red", AnkiText.spokenText("The ___ is red"))
    }
}
