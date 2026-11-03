package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h110b: `face.spoken` is already plain text (tags stripped, entities decoded), so it must use
 *  `speakablePlain` (prose + sanitize only). `speakable`'s tag regex would delete everything
 *  between a literal "<" and ">". */
class H110bTest {
    @Test fun plainKeepsLiteralComparison() {
        val result = SpeakableText.speakablePlain("Is a < b and c > d true?")
        assertTrue("plain text must keep a literal '<' '>' ('b and c'), got: " + result, result.contains("b and c"))
    }

    @Test fun speakableDropsBetweenAngleBrackets() {
        val out = SpeakableText.speakable("Is a < b and c > d true?")
        assertFalse("documents why speakablePlain exists: tag regex eats '<...>'", out.contains("b and c"))
    }

    @Test fun plainAndSpeakableAgreeOnIdentifiers() {
        assertEquals(SpeakableText.speakable("Use user_id here"), SpeakableText.speakablePlain("Use user_id here"))
    }
}
