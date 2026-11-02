package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKeysTest {

    @Test
    fun `play pause down toggles`() {
        assertEquals(MediaKeys.Action.TOGGLE, MediaKeys.actionFor(85, true, 10_000, 0))
    }

    @Test
    fun `play down toggles`() {
        assertEquals(MediaKeys.Action.TOGGLE, MediaKeys.actionFor(126, true, 10_000, 0))
    }

    @Test
    fun `pause down toggles`() {
        assertEquals(MediaKeys.Action.TOGGLE, MediaKeys.actionFor(127, true, 10_000, 0))
    }

    @Test
    fun `headsethook down toggles`() {
        assertEquals(MediaKeys.Action.TOGGLE, MediaKeys.actionFor(79, true, 10_000, 0))
    }

    @Test
    fun `up events ignored`() {
        for (k in listOf(79, 85, 126, 127)) {
            assertEquals(MediaKeys.Action.IGNORE, MediaKeys.actionFor(k, false, 10_000, 0))
        }
    }

    @Test
    fun `other keys ignored`() {
        assertEquals(MediaKeys.Action.IGNORE, MediaKeys.actionFor(87, true, 10_000, 0))
        assertEquals(MediaKeys.Action.IGNORE, MediaKeys.actionFor(24, true, 10_000, 0))
    }

    @Test
    fun `debounce inside 600 ms ignored`() {
        assertEquals(MediaKeys.Action.IGNORE, MediaKeys.actionFor(85, true, 10_599, 10_000))
        assertEquals(MediaKeys.Action.IGNORE, MediaKeys.actionFor(79, true, 10_001, 10_000))
    }

    @Test
    fun `debounce boundary toggles`() {
        assertEquals(MediaKeys.Action.TOGGLE, MediaKeys.actionFor(85, true, 10_600, 10_000))
        assertEquals(MediaKeys.Action.TOGGLE, MediaKeys.actionFor(85, true, 11_000, 10_000))
    }

    @Test
    fun `first press never debounced`() {
        assertEquals(MediaKeys.Action.TOGGLE, MediaKeys.actionFor(85, true, 100, 0))
    }
}
