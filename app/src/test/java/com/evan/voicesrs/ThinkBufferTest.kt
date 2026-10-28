package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkBufferTest {

    @Test
    fun `a joins two windows`() {
        val b = ThinkBuffer()
        b.append("the capital")
        b.append("is canberra")
        assertEquals("the capital is canberra", b.text())
    }

    @Test
    fun `b overlap is de-duplicated`() {
        val b = ThinkBuffer()
        b.append("capital is")
        b.append("is canberra")
        assertEquals("capital is canberra", b.text())
    }

    @Test
    fun `c blanks ignored`() {
        val b = ThinkBuffer()
        b.append("")
        b.append(null)
        b.append("  ")
        assertTrue(b.isEmpty())
    }

    @Test
    fun `d clear empties`() {
        val b = ThinkBuffer()
        b.append("x")
        assertFalse(b.isEmpty())
        b.clear()
        assertTrue(b.isEmpty())
    }
}
