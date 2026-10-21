package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContentHashTest {
    @Test
    fun `hello world is the golden sha256`() {
        assertEquals(
            "8ebb91a54e90cae8117786e3e25bd888a20233272c60e4859ae30c737766dce2",
            ContentHash.of(1, "Hello", "World")
        )
    }

    @Test
    fun `hi there is the golden sha256`() {
        assertEquals(
            "3382aa3fac1beadba63a82eea5f0cf69eb9bcd7080081d34ab5e43f85922bf4b",
            ContentHash.of(1, "Hi", "There")
        )
    }

    @Test
    fun `html is stripped before hashing`() {
        assertEquals(
            ContentHash.of(1, "Hello", "World"),
            ContentHash.of(1, "<b>Hello</b>", "<i>World</i>")
        )
    }

    @Test
    fun `prompt version changes the hash`() {
        assertNotEquals(ContentHash.of(1, "Hello", "World"), ContentHash.of(2, "Hello", "World"))
    }
}
