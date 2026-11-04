package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * h143b: the CALL read-aloud channel must keep the Bluetooth SCO headset as the
 * communication device for TTS. The watcher never drives it (see H143Test); the
 * service re-decides it in speak() before TTS so read-aloud rides the call
 * channel again.
 */
class H143bTest {

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
        error("source not found: ${segments.joinToString("/")}")
    }

    private val speaker = MicRoute.AudioDev(1, MicRoute.AudioDevType.BUILTIN_SPEAKER)
    private val sco = MicRoute.AudioDev(7, MicRoute.AudioDevType.BLUETOOTH_SCO)
    private val a2dp = MicRoute.AudioDev(8, MicRoute.AudioDevType.BLUETOOTH_A2DP)

    /** (a) CALL keeps the first SCO output. */
    @Test
    fun ttsCommOutputCallKeepsSco() {
        val out = AudioRouteDecision.ttsCommOutput(Prefs.READ_ALOUD_CALL, listOf(speaker, sco))
        assertEquals(sco, out)
    }

    /** (b) MEDIA never keeps SCO even when one is present. */
    @Test
    fun ttsCommOutputMediaNeverSco() {
        assertNull(AudioRouteDecision.ttsCommOutput(Prefs.READ_ALOUD_MEDIA, listOf(sco)))
    }

    /** (c) CALL with no SCO output (only A2DP) keeps nothing. */
    @Test
    fun ttsCommOutputCallNoScoReturnsNull() {
        assertNull(AudioRouteDecision.ttsCommOutput(Prefs.READ_ALOUD_CALL, listOf(speaker, a2dp)))
    }

    /** (d) speak() now drives the comm route: ttsCommOutput + setCommDevice, and the MEDIA path still clears. */
    @Test
    fun speakDrivesCommRoute() {
        val drill = source(
            "app", "src", "main", "java", "com", "evan", "voicesrs", "DrillService.kt",
        )
        val speak = Regex("override fun speak\\((.*?)\\)(.*?)\\n    \\}", RegexOption.DOT_MATCHES_ALL)
            .find(drill)?.value.orEmpty()
        assertTrue("speak() must call ttsCommOutput", speak.contains("ttsCommOutput"))
        assertTrue("speak() must set the comm device on the CALL path", speak.contains("setCommDevice("))
        assertTrue("speak() must still clear the comm route on the MEDIA path", speak.contains("clearCommRoute()"))
    }

    /** (e) The watcher's evaluate() still never touches the communication device. */
    @Test
    fun evaluateStillDoesNotDriveCommDevice() {
        val watcher = source(
            "app", "src", "main", "java", "com", "evan", "voicesrs", "AudioRouteWatcher.kt",
        )
        val evaluate = Regex("private fun evaluate\\(\\)(.*?)\\n    \\}", RegexOption.DOT_MATCHES_ALL)
            .find(watcher)?.value.orEmpty()
        assertFalse("evaluate() must not call setCommunicationDevice(", evaluate.contains("setCommunicationDevice("))
        assertFalse("evaluate() must not call clearCommunicationDevice(", evaluate.contains("clearCommunicationDevice()"))
    }
}
