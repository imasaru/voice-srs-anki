package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepScreenOnTest {
    @Test
    fun `flag is on only while a drill runs and the setting is on`() {
        assertFalse(KeepScreenOn.SETTING_DEFAULT)
        assertTrue(KeepScreenOn.apply(drillRunning = true, enabled = true))
        assertFalse(KeepScreenOn.apply(drillRunning = true, enabled = false))
        assertFalse(KeepScreenOn.apply(drillRunning = false, enabled = true))
        assertEquals(false, KeepScreenOn.apply(false, false))
    }
}
