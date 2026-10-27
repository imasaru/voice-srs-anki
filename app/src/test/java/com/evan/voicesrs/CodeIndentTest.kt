package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeIndentTest {

    @Test
    fun `leading tab expands to four nbsp and inner spaces stay`() {
        assertEquals("\u00A0\u00A0\u00A0\u00A0SELECT a,  b", CodeIndent.expand("\tSELECT a,  b"))
    }

    @Test
    fun `leading spaces become nbsp while inner spaces stay`() {
        assertEquals("\u00A0\u00A0x = 1", CodeIndent.expand("  x = 1"))
    }

    @Test
    fun `empty and no-leading-whitespace lines pass through`() {
        assertEquals("", CodeIndent.expand(""))
        assertEquals("z", CodeIndent.expand("z"))
        // a tab that is not at the start is left untouched
        assertEquals("a\tb", CodeIndent.expand("a\tb"))
    }

    @Test
    fun `depth counts leading spaces after tab expansion`() {
        assertEquals(6, CodeIndent.depth("\t  y"))
        assertEquals(0, CodeIndent.depth("z"))
        assertEquals(4, CodeIndent.depth("    x"))
        assertEquals(0, CodeIndent.depth(""))
    }
}
