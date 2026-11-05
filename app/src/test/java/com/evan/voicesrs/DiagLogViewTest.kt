package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** h153p: DiagLogView parse/read/filter/clear. */
class DiagLogViewTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun a_parseLineMapsLevelAndTime() {
        val r = DiagLogView.parseLine("2026-11-05T13:29:16.412+09:00 DrillService I msg=\"app start\"")!!
        assertEquals("13:29:16", r.time)
        assertEquals("INFO", r.level)
        assertEquals("DrillService I msg=\"app start\"", r.message)
        assertEquals("WARNING", DiagLogView.levelOf("W"))
        assertEquals("ERROR", DiagLogView.levelOf("E"))
        assertEquals("DEBUG", DiagLogView.levelOf("DEBUG"))
        assertNull(DiagLogView.parseLine("not-a-log"))
    }

    @Test fun b_readLastKeepsNewestLimitOldestFirst() {
        val dir = tmp.newFolder("logs")
        File(dir, "diag-1.log").writeText("2026-11-05T13:00:00.000+09:00 T E old\n")
        File(dir, "diag-0.log").writeText(
            (1..5).joinToString("") { i ->
                "2026-11-05T13:00:0$i.000+09:00 T I n=$i\n"
            }
        )
        val rows = DiagLogView.readLast(dir, limit = 3)
        assertEquals(3, rows.size)
        assertEquals("n=3", rows[0].message.substringAfter("I ").trim())
        assertEquals("n=5", rows[2].message.substringAfter("I ").trim())
        assertEquals("INFO", rows[2].level)
        assertTrue(DiagLogView.readLast(File(dir, "missing")).isEmpty())
    }

    @Test fun c_filterByLevel() {
        val rows = listOf(
            DiagLogView.Row("13:00:01", "INFO", "a", "a"),
            DiagLogView.Row("13:00:02", "ERROR", "b", "b"),
            DiagLogView.Row("13:00:03", "INFO", "c", "c"),
        )
        assertEquals(listOf("a", "c"), DiagLogView.filter(rows, setOf("INFO")).map { it.message })
        assertTrue(DiagLogView.filter(rows, emptySet()).isEmpty())
    }

    @Test fun d_clearDeletesDiagFiles() {
        val dir = tmp.newFolder("logs2")
        File(dir, "diag-0.log").writeText("x\n")
        File(dir, "diag-1.log").writeText("y\n")
        File(dir, "other.txt").writeText("z\n")
        assertTrue(DiagLogView.clear(dir))
        assertTrue(!File(dir, "diag-0.log").exists())
        assertTrue(!File(dir, "diag-1.log").exists())
        assertTrue(File(dir, "other.txt").exists())
    }

    @Test fun e_levelsList() {
        assertEquals(listOf("TRACE", "DEBUG", "INFO", "WARNING", "ERROR"), DiagLogView.LEVELS)
        assertEquals(200, DiagLogView.LIMIT)
    }
}
