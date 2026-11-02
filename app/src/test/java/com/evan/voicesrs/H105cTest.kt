package com.evan.voicesrs

import com.evan.voicesrs.MicRoute.AudioDev
import com.evan.voicesrs.MicRoute.AudioDevType
import com.evan.voicesrs.MicRouteTiming
import com.evan.voicesrs.MicRouteTiming.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h105c: the pure route decision + log line for the communication-device switch. */
class H105cTest {

    private val sco = AudioDev(2, AudioDevType.BLUETOOTH_SCO, "WH-1000XM3")
    private val media = Prefs.READ_ALOUD_MEDIA

    @Test
    fun `answer listens route to the headset mic`() {
        assertTrue(MicRouteTiming.shouldRoute(listenKind = Kind.ANSWER, dev = sco))
    }

    @Test
    fun `rating listens route to the headset mic`() {
        assertTrue(MicRouteTiming.shouldRoute(listenKind = Kind.RATING, dev = sco))
    }

    @Test
    fun `command listens never route`() {
        assertFalse(MicRouteTiming.shouldRoute(listenKind = Kind.COMMAND, dev = sco))
    }

    @Test
    fun `read-aloud never route`() {
        assertFalse(MicRouteTiming.shouldRoute(listenKind = Kind.READ_ALOUD, dev = sco))
    }

    @Test
    fun `no device never route`() {
        assertFalse(MicRouteTiming.shouldRoute(listenKind = Kind.ANSWER, dev = null))
    }

    @Test
    fun `route log is default with no device`() {
        assertEquals(
            "mic route: default",
            MicRouteTiming.routeLog(dev = null, setting = true, channel = media)
        )
    }

    @Test
    fun `route log names the SCO device`() {
        val log = MicRouteTiming.routeLog(dev = sco, setting = true, channel = media)
        assertTrue(
            "log was: $log",
            log.startsWith("mic route: BLUETOOTH_SCO WH-1000XM3 (setting=on channel=")
        )
    }
}
