package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * h143: a BT headset plugged in mid-drill must not strand the answer listen on a
 * cleared / not-yet-active SCO route. The watcher only flags a route change; the
 * service re-opens the listen once; the Whisper engine only pins SCO when the
 * comm route actually came up.
 */
class H143Test {

    /** Walk up from the working dir to a source file under the app's package. */
    private fun source(vararg segments: String): String {
        var dir = File(".").absoluteFile
        while (true) {
            val candidate = File(dir, segments.joinToString("/"))
            if (candidate.isFile) return candidate.readText()
            val parent = dir.parentFile ?: break
            if (parent == dir) break
            dir = parent
        }
        error("source not found: ${segments.joinToString("/")} (cwd=${File(".").absoluteFile})")
    }

    /** (a) The watcher must never set or clear the communication device itself. */
    @Test
    fun watcherDoesNotDriveCommunicationDevice() {
        val watcher = source(
            "app", "src", "main", "java", "com", "evan", "voicesrs", "AudioRouteWatcher.kt",
        )
        // No setCommunicationDevice( call anywhere in the watcher.
        assertFalse("AudioRouteWatcher must not call setCommunicationDevice(",
            watcher.contains("setCommunicationDevice("))
        // The evaluate() body in particular must not clear the comm device.
        val evaluate = Regex("private fun evaluate\\(\\)(.*?)\\n    \\}",
            RegexOption.DOT_MATCHES_ALL).find(watcher)?.value.orEmpty()
        assertFalse("evaluate() must not call clearCommunicationDevice()",
            evaluate.contains("clearCommunicationDevice()"))
    }

    /** (b) RouteRebuild.reopenOnRouteChange caps at one reopen, only for ANSWER/RATING. */
    @Test
    fun reopenReroutesAnswerOnce() {
        assertTrue(
            RouteRebuild.reopenOnRouteChange(MicRouteTiming.Kind.ANSWER, changed = true, alreadyReopened = false),
        )
    }

    @Test
    fun reopenSkipsWhenAlreadyReopened() {
        assertFalse(
            RouteRebuild.reopenOnRouteChange(MicRouteTiming.Kind.ANSWER, changed = true, alreadyReopened = true),
        )
    }

    @Test
    fun reopenNeverForCommand() {
        assertFalse(
            RouteRebuild.reopenOnRouteChange(MicRouteTiming.Kind.COMMAND, changed = true, alreadyReopened = false),
        )
    }

    @Test
    fun reopenNoOpWhenRouteUnchanged() {
        assertFalse(
            RouteRebuild.reopenOnRouteChange(MicRouteTiming.Kind.ANSWER, changed = false, alreadyReopened = false),
        )
    }

    /** (c) Only pin the SCO device when the comm route actually came up. */
    @Test
    fun preferWhenOnlySurfacesDeviceWhenCommActive() {
        val sco = "blue-sco-mic"
        assertNull(ListenMic.preferWhen(commActive = false, dev = sco))
        assertEquals(sco, ListenMic.preferWhen(commActive = true, dev = sco))
    }

    /** (d) The service logs the reopen so the reroute is visible in logcat. */
    @Test
    fun serviceLogsRouteReopen() {
        val drill = source(
            "app", "src", "main", "java", "com", "evan", "voicesrs", "DrillService.kt",
        )
        assertTrue("DrillService should log the route reopen",
            drill.contains("mic route: reopen after device change"))
    }
}
