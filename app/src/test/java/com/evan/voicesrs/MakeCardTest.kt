package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MakeCardTest {
    @Test
    fun `parse Q and A lines`() {
        val draft = MakeCard.parse(
            """
            Q: What does Delta Lake add on Parquet?
            A: A JSON transaction log with ACID guarantees.
            """.trimIndent()
        )
        assertEquals("What does Delta Lake add on Parquet?", draft?.first)
        assertEquals("A JSON transaction log with ACID guarantees.", draft?.second)
        assertEquals(
            "Front",
            MakeCard.parse("Front: Front\nBack: Back")?.first
        )
        assertNull(MakeCard.parse("nope"))
        assertEquals(MakeCard.DEFAULT_DECK, "Databricks DEA::Generated")
        assertEquals("ai-generated", MakeCard.TAG)
        assertTrue(MakeCard.system().contains("Basic"))
    }
}
