package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagLogFileTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun a_writesLineToDiag0() {
        val f = DiagLogFile(tmp.newFolder("a"))
        f.appendNow("x")
        f.appendNow("y")
        assertEquals("x\ny\n", f.file(0).readText())
        assertFalse(f.file(1).exists())
    }

    @Test fun b_rotatesAtCap() {
        val f = DiagLogFile(tmp.newFolder("b"), capBytes = 10)
        f.appendNow("aaaaaaaa")
        f.appendNow("bbbbbbbb")
        assertEquals("bbbbbbbb\n", f.file(0).readText())
        assertEquals("aaaaaaaa\n", f.file(1).readText())
    }

    @Test fun c_keepsFiveFiles_dropsOldestOnSixth() {
        val f = DiagLogFile(tmp.newFolder("c"), capBytes = 5)
        for (n in 0..5) f.appendNow("l$n")
        for (i in 0..4) assertEquals("l${5 - i}\n", f.file(i).readText())
        assertFalse(f.file(5).exists())
    }

    @Test fun d_writeFailureDoesNotThrow() {
        val notDir = tmp.newFile("plain")
        val f = DiagLogFile(notDir)
        f.appendNow("x")
        assertEquals(1, f.failures)
    }
}
