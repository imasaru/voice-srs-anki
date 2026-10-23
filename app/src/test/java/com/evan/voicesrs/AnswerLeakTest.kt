package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerLeakTest {
    private val q21 = """
        A PIVOT query in Databricks SQL rotates which values into columns?
        A. UNPIVOT
        B. PIVOT
        C. STACK
        D. MELT
    """.trimIndent()

    @Test
    fun `Q21 hint that names PIVOT or letter B is a leak`() {
        val secrets = AnswerLeak.secrets(q21, "B")
        assertEquals("B", secrets.letter)
        assertTrue(secrets.phrases.any { it.equals("PIVOT", ignoreCase = true) })
        assertTrue(AnswerLeak.leaked("PIVOT rotates rows into columns.", secrets))
        assertTrue(AnswerLeak.leaked("The answer is B.", secrets))
        assertTrue(AnswerLeak.leaked("Option B is the one you want.", secrets))
        assertFalse(AnswerLeak.leaked("Think about rotating rows into columns.", secrets))
        assertFalse(AnswerLeak.leaked("UNPIVOT is the reverse operation.", secrets))
    }

    @Test
    fun `redact strips the correct option text and letter claim`() {
        val secrets = AnswerLeak.secrets(q21, "B")
        val redacted = AnswerLeak.redact(
            "PIVOT is right. The answer is B.",
            secrets
        )
        assertFalse(AnswerLeak.leaked(redacted, secrets))
        assertFalse(redacted.contains("PIVOT", ignoreCase = true))
        assertTrue(redacted.contains(AnswerLeak.PLACEHOLDER))
    }

    @Test
    fun `guard keeps a clean reply and redacts after a leaking retry`() {
        val secrets = AnswerLeak.secrets(q21, "B")
        assertEquals(
            "Think about rotating rows.",
            AnswerLeak.guard("Think about rotating rows.", secrets) { "PIVOT" }
        )
        var retried = false
        val recovered = AnswerLeak.guard("PIVOT is the operator.", secrets) {
            retried = true
            "Think about rotating rows into columns."
        }
        assertTrue(retried)
        assertEquals("Think about rotating rows into columns.", recovered)
        val redacted = AnswerLeak.guard("PIVOT is the operator.", secrets) { "Still PIVOT." }
        assertFalse(AnswerLeak.leaked(redacted, secrets))
        assertFalse(redacted.contains("PIVOT", ignoreCase = true))
    }

    @Test
    fun `without a Correct field there is nothing to leak`() {
        val secrets = AnswerLeak.secrets(q21, "")
        assertTrue(secrets.empty)
        assertFalse(AnswerLeak.leaked("PIVOT is the operator.", secrets))
    }
}
