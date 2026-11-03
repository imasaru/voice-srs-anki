package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class H117cTest {

    @Test
    fun activeExcludesSuspended() {
        val s = StateCounts(2, 2, 1, 2, 2)
        assertEquals(5, s.active)
        assertEquals(7, s.total)
    }

    @Test
    fun dueAndNewQueriesExcludeSuspendedAndBuried() {
        val lines = java.io.File("src/main/java/com/evan/voicesrs/AnkiConnect.kt").readLines()
        val dueLine = lines.first { it.contains("findCards") && it.contains("is:due") }
        val newLine = lines.first { it.contains("findCards") && it.contains("is:new") }
        assertNotNull(dueLine)
        assertNotNull(newLine)
        assertTrue(dueLine.contains("-is:suspended -is:buried"))
        assertTrue(newLine.contains("-is:suspended -is:buried"))
    }
}
