package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/**
 * Pins the exact slim top-bar subtitle text so the later layout (53B) and
 * keyboard-collapse (53C) wiring stay in lockstep with the approved mockup.
 */
class SlimBarTextTest {

    @Test
    fun `elapsed formats under an hour as m-ss`() {
        assertEquals("Elapsed 0:00", SlimBarText.elapsed(0))
        assertEquals("Elapsed 0:05", SlimBarText.elapsed(5_000))
        assertEquals("Elapsed 12:34", SlimBarText.elapsed(754_000))
        assertEquals("Elapsed 0:59", SlimBarText.elapsed(59_999))
    }

    @Test
    fun `elapsed formats at or over an hour as h-mm-ss`() {
        assertEquals("Elapsed 1:02:03", SlimBarText.elapsed(3_723_000))
    }

    @Test
    fun `elapsed treats negative as zero`() {
        assertEquals("Elapsed 0:00", SlimBarText.elapsed(-5))
    }

    @Test
    fun `block uses goal when goal is positive`() {
        assertEquals("34/90 min", SlimBarText.block(34, 90))
    }

    @Test
    fun `block omits goal when goal is zero`() {
        assertEquals("34 min", SlimBarText.block(34, 0))
    }

    @Test
    fun `block clamps negative to zero`() {
        assertEquals("0/90 min", SlimBarText.block(-3, 90))
    }

    @Test
    fun `idle names due count then block`() {
        assertEquals("12 due · 34/90 min", SlimBarText.idle(12, 34, 90))
        assertEquals("0 due · 0/90 min", SlimBarText.idle(0, 0, 90))
        assertEquals("0 due · 5 min", SlimBarText.idle(-1, 5, 0))
    }

    @Test
    fun `drilling prefixes status then elapsed then block`() {
        assertEquals(
            "Reading the question · Elapsed 12:34 · 34/90 min",
            SlimBarText.drilling("Reading the question", 754_000, 34, 90),
        )
    }

    @Test
    fun `drilling blanks the status to Drilling`() {
        assertEquals(
            "Drilling · Elapsed 0:00 · 1/90 min",
            SlimBarText.drilling("  ", 0, 1, 90),
        )
    }

    @Test
    fun `paused prefixes Paused then elapsed then block`() {
        assertEquals("Paused · Elapsed 12:34 · 34/90 min", SlimBarText.paused(754_000, 34, 90))
    }

    @Test
    fun `keyboardLine names deck then block with goal`() {
        assertEquals("Databricks · 34/90", SlimBarText.keyboardLine(" Databricks ", 34, 90))
    }

    @Test
    fun `keyboardLine names deck then block without goal`() {
        assertEquals("D · 34 min", SlimBarText.keyboardLine("D", 34, 0))
    }
}
