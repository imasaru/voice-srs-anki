package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaBtnPlanTest {

    @Test
    fun `toggle pauses a running drill and resumes a paused one`() {
        assertEquals(MediaBtnPlan.Plan.PAUSE, MediaBtnPlan.next(paused = false, MediaKeys.Action.TOGGLE))
        assertEquals(MediaBtnPlan.Plan.RESUME, MediaBtnPlan.next(paused = true, MediaKeys.Action.TOGGLE))
    }

    @Test
    fun `ignore is none whether or not the drill is paused`() {
        assertEquals(MediaBtnPlan.Plan.NONE, MediaBtnPlan.next(paused = false, MediaKeys.Action.IGNORE))
        assertEquals(MediaBtnPlan.Plan.NONE, MediaBtnPlan.next(paused = true, MediaKeys.Action.IGNORE))
    }
}
