package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoMatchPartialTest {
    @Test
    fun `a no match after speech keeps the partial`() {
        assertEquals("is canberra", ListenWindow.noMatchPartial(ListenWindow.ERROR_NO_MATCH, true, listOf(" is canberra ", "is camberra")))
    }

    @Test
    fun `b blank partials or no speech give null`() {
        assertNull(ListenWindow.noMatchPartial(ListenWindow.ERROR_NO_MATCH, true, listOf("", "  ")))
        assertNull(ListenWindow.noMatchPartial(ListenWindow.ERROR_NO_MATCH, false, listOf("is canberra")))
    }

    @Test
    fun `c speech timeout and other errors give null`() {
        assertNull(ListenWindow.noMatchPartial(ListenWindow.ERROR_SPEECH_TIMEOUT, true, listOf("is canberra")))
        assertNull(ListenWindow.noMatchPartial(5, true, listOf("is canberra")))
    }
}
