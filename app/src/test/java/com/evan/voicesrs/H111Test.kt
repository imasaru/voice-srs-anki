package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** h111: no built-in speech server address. Blank Speech server URL = no Whisper, no Vosk download. */
class H111Test {
    @Test
    fun `a blank asr url is no server`() {
        assertNull(SpeechEngine.asrBase(""))
        assertNull(SpeechEngine.asrBase("   "))
        assertNull(SpeechEngine.asrBase("/"))
    }

    @Test
    fun `b set asr url is used as typed without trailing slash`() {
        assertEquals("http://a:9", SpeechEngine.asrBase("http://a:9/"))
        assertEquals("https://b/x", SpeechEngine.asrBase(" https://b/x "))
    }

    @Test
    fun `c vosk download needs a server url`() {
        assertFalse(SpeechEngine.shouldFetchVosk(null, SpeechChoice.AUTO, voskReady = false, unmetered = true))
        assertFalse(SpeechEngine.shouldFetchVosk(null, SpeechChoice.VOSK, voskReady = false, unmetered = true))
        assertTrue(SpeechEngine.shouldFetchVosk("http://a:9", SpeechChoice.AUTO, voskReady = false, unmetered = true))
        assertFalse(SpeechEngine.shouldFetchVosk("http://a:9", SpeechChoice.GOOGLE, voskReady = false, unmetered = true))
        assertFalse(SpeechEngine.shouldFetchVosk("http://a:9", SpeechChoice.AUTO, voskReady = true, unmetered = true))
        assertFalse(SpeechEngine.shouldFetchVosk("http://a:9", SpeechChoice.AUTO, voskReady = false, unmetered = false))
    }

    @Test
    fun `d auto with no server and no model is google only`() {
        assertEquals(listOf(EngineKind.GOOGLE), SpeechEngine.order(SpeechChoice.AUTO, whisperReachable = false, voskModelReady = false))
    }

    @Test
    fun `e defaults are blank`() {
        assertEquals("", AnkiConfig(url = "", apiKey = "", deck = "").asrUrl)
        assertEquals("", PrefsMap.typedDefaults()["asr_url"])
    }

    @Test
    fun `f speech server url row sits under the speech engine selector`() {
        val xml = srcFile("main/res/layout/activity_settings.xml").readText()
        val help = xml.indexOf("@+id/speechEngineHelp")
        val row = xml.indexOf("@+id/asrUrlInput")
        assertTrue("asrUrlInput missing", row > 0)
        assertTrue("asrUrlInput must come after speechEngineHelp", row > help && help > 0)
        val strings = srcFile("main/res/values/strings.xml").readText()
        assertTrue(strings.contains(">Speech server URL<"))
    }
}

/** Unit tests run with the module dir (app/) as cwd; fall back to repo root. */
internal fun srcFile(rel: String): File {
    val a = File("src/$rel")
    return if (a.exists()) a else File("app/src/$rel")
}
