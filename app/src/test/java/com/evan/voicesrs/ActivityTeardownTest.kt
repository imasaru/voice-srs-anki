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
}
