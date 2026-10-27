package com.evan.voicesrs

import com.evan.voicesrs.PlaybackControl.State
import com.evan.voicesrs.PlaybackControl.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class DrillButtonTest {

    @Test
    fun `idle with no service reads Start`() {
        assertEquals(false, PlaybackControl.drillButtonStops(false, false))
    }

    @Test
    fun `drill running reads Stop`() {
        assertEquals(true, PlaybackControl.drillButtonStops(true, false))
    }

    @Test
    fun `service up before UI state reads Stop`() {
        assertEquals(true, PlaybackControl.drillButtonStops(false, true))
    }

    @Test
    fun `paused drill still running reads Stop`() {
        assertEquals(true, PlaybackControl.drillButtonStops(true, true))
    }

    @Test
    fun `idle running drill not paused shows Pause`() {
        val idle = Snapshot()
        assertEquals(true, PlaybackControl.pauseShowsDrillPause(idle, true, false))
    }

    @Test
    fun `paused drill shows Resume branch not Pause`() {
        val idle = Snapshot()
        assertEquals(false, PlaybackControl.pauseShowsDrillPause(idle, true, true))
    }

    @Test
    fun `read-aloud active shows Resume branch not Pause`() {
        val reading = Snapshot(state = State.READING)
        assertEquals(false, PlaybackControl.pauseShowsDrillPause(reading, true, false))
    }

    @Test
    fun `not drilling shows not Pause`() {
        val idle = Snapshot()
        assertEquals(false, PlaybackControl.pauseShowsDrillPause(idle, false, false))
    }
}
