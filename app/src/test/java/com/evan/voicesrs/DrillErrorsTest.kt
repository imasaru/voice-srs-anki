package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillErrorsTest {
    @Test
    fun `visible failure strings start with Drill stopped and fit one dump node`() {
        assertEquals("Drill stopped", DrillErrors.STOPPED)
        assertTrue(DrillErrors.ANKI.startsWith(DrillErrors.STOPPED))
        assertTrue(DrillErrors.GENERIC.startsWith(DrillErrors.STOPPED))
        assertTrue(DrillErrors.STOPPED.length < 80)
        assertTrue(DrillErrors.ANKI.length < 80)
        assertTrue(DrillErrors.GENERIC.length < 80)
    }
}
