package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckNameTest {
    @Test
    fun `nested deck splits path and leaf`() {
        assertEquals(
            "Databricks DEA › kengio" to "Mock Exam 1",
            DeckName.split("Databricks DEA::kengio::Mock Exam 1")
        )
        assertEquals("A › B" to "C", DeckName.split("A::B::C"))
    }

    @Test
    fun `top-level deck has a blank path`() {
        assertEquals("" to "VoiceSRS-Test", DeckName.split("VoiceSRS-Test"))
        assertEquals("" to "Default", DeckName.split("Default"))
    }

    @Test
    fun `trailing and empty parts are ignored`() {
        assertEquals("A" to "B", DeckName.split("A::B::"))
        assertEquals("A" to "B", DeckName.split("A::::B"))
        assertEquals("" to "A", DeckName.split("::A::"))
        assertEquals("A › B" to "C", DeckName.split("A::B::C::"))
        assertEquals("A" to "B", DeckName.split("::A::B"))
    }

    @Test
    fun `parts are trimmed`() {
        assertEquals(
            "Databricks DEA › kengio" to "Mock Exam 1",
            DeckName.split("  Databricks DEA :: kengio :: Mock Exam 1  ")
        )
        assertEquals("A" to "B", DeckName.split(" A :: B "))
        assertEquals("" to "Leaf", DeckName.split("  Leaf  "))
    }

    @Test
    fun `blank input is empty path and leaf`() {
        assertEquals("" to "", DeckName.split(""))
        assertEquals("" to "", DeckName.split("   "))
        assertEquals("" to "", DeckName.split("::"))
        assertEquals("" to "", DeckName.split(" :: :: "))
    }

    @Test
    fun `changedMidDrill flags a different deck while drilling`() {
        assertEquals(true, DeckName.changedMidDrill("A::B", "A::C", true))
    }

    @Test
    fun `changedMidDrill is false when the deck is unchanged`() {
        assertEquals(false, DeckName.changedMidDrill("A::B", "A::B", true))
    }

    @Test
    fun `changedMidDrill ignores surrounding whitespace`() {
        assertEquals(false, DeckName.changedMidDrill("A::B", " A::B ", true))
    }

    @Test
    fun `changedMidDrill is false when no drill runs`() {
        assertEquals(false, DeckName.changedMidDrill("A::B", "A::C", false))
    }

    @Test
    fun `changedMidDrill is false when drill started before this activity`() {
        assertEquals(false, DeckName.changedMidDrill(null, "A::C", true))
    }

    @Test
    fun `changedMidDrill flags a blank stored deck`() {
        assertEquals(true, DeckName.changedMidDrill("", "A", true))
    }
}
