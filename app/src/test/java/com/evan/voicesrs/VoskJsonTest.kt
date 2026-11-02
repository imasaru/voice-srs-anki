package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoskJsonTest {

    @Test
    fun `partial text is read and a blank partial is null`() {
        assertEquals("data skew", VoskJson.partial("""{"partial":"data skew"}"""))
        assertNull(VoskJson.partial("""{"partial":""}"""))
    }

    @Test
    fun `result text is read`() {
        assertEquals("merge into", VoskJson.text("""{"text":"merge into"}"""))
    }
}
