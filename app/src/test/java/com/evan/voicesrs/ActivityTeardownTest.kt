package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h81: a configuration-change destroy keeps read-aloud playing. */
class ActivityTeardownTest {
    @Test
    fun `a changing configurations does not stop playback`() {
        assertFalse(ActivityTeardown.shouldStopPlayback(isChangingConfigurations = true))
    }

    @Test
    fun `b a real destroy stops playback`() {
        assertTrue(ActivityTeardown.shouldStopPlayback(isChangingConfigurations = false))
    }

    @Test
    fun `c a real destroy while drilling keeps playback`() {
        assertFalse(ActivityTeardown.shouldStopPlayback(isChangingConfigurations = false, drilling = true))
    }

    @Test
    fun `d a recreate while drilling keeps playback`() {
        assertFalse(ActivityTeardown.shouldStopPlayback(isChangingConfigurations = true, drilling = true))
    }
}
