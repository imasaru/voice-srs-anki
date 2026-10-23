package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiFieldsTest {
    @Test
    fun `Correct value is the letter and a missing field is blank`() {
        val fields = JSONObject(
            """{"Front":{"value":"stem","order":0},"Correct":{"value":"B","order":2}}"""
        )
        assertEquals("B", AnkiFields.correct(fields))
        assertEquals("stem", AnkiFields.value(fields, "Front"))
        assertTrue(AnkiFields.has(fields, AnkiFields.CORRECT))
        assertFalse(AnkiFields.has(fields, "Missing"))
        assertEquals("", AnkiFields.correct(JSONObject()))
        assertEquals("", AnkiFields.correct(null))
        assertEquals("C", AnkiFields.value(JSONObject().put("Correct", " C "), AnkiFields.CORRECT))
    }
}
