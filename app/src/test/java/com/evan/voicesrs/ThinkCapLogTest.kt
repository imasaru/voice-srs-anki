package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkCapLogTest {
    @Test
    fun `a first restart past the cap is reported`() {
        assertTrue(ThinkTime.capJustReached(ThinkTime.MAX_RESTARTS + 1))
    }

    @Test
    fun `b below the cap and later restarts are not reported`() {
        assertFalse(ThinkTime.capJustReached(ThinkTime.MAX_RESTARTS))
        assertFalse(ThinkTime.capJustReached(ThinkTime.MAX_RESTARTS + 2))
        assertFalse(ThinkTime.capJustReached(0))
    }
}
