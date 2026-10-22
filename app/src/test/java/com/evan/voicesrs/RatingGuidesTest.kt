package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingGuidesTest {
    @Test
    fun `first three cards get the long prompt`() {
        assertEquals(RatingGuides.LONG, RatingGuides.initialPrompt(0, false))
        assertEquals(RatingGuides.LONG, RatingGuides.initialPrompt(1, false))
        assertEquals(RatingGuides.LONG, RatingGuides.initialPrompt(2, false))
    }

    @Test
    fun `later cards get the short prompt`() {
        assertEquals(RatingGuides.SHORT, RatingGuides.initialPrompt(3, false))
        assertEquals(RatingGuides.SHORT, RatingGuides.initialPrompt(10, false))
    }

    @Test
    fun `speed mode skips the opening prompt and the reprompt`() {
        assertNull(RatingGuides.initialPrompt(0, true))
        assertNull(RatingGuides.initialPrompt(4, true))
        assertNull(RatingGuides.reprompt(true))
    }

    @Test
    fun `reprompt names the four grades`() {
        assertEquals("Say a grade: again, hard, good or easy", RatingGuides.reprompt(false))
    }
}
