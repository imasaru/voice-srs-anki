package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeInsetsTest {
    @Test
    fun `keyboard hidden uses nav bottom`() {
        // imeBottom = 0 when the keyboard is hidden.
        assertEquals(12 + 48, ImeInsets.bottomPadding(base = 12, imeBottom = 0, navBottom = 48))
    }

    @Test
    fun `keyboard shown uses ime bottom`() {
        assertEquals(12 + 140, ImeInsets.bottomPadding(base = 12, imeBottom = 140, navBottom = 48))
    }

    @Test
    fun `ime smaller than nav falls back to nav`() {
        assertEquals(12 + 48, ImeInsets.bottomPadding(base = 12, imeBottom = 20, navBottom = 48))
    }

    @Test
    fun `ThreadSheet wires in insets and resize mode`() {
        val src = File("src/main/java/com/evan/voicesrs/ThreadSheet.kt")
        assertTrue("source file missing: ${src.absolutePath}", src.isFile)
        val text = src.readText()
        assertTrue("missing WindowInsetsCompat.Type.ime()", text.contains("WindowInsetsCompat.Type.ime()"))
        assertTrue("missing SOFT_INPUT_ADJUST_RESIZE", text.contains("SOFT_INPUT_ADJUST_RESIZE"))
    }
}
