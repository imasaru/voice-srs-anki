package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class H120aTest {

    @Test
    fun parseDefaultsToEnglishForUnknownOrMissing() {
        assertSame(AsrLanguage.EN, AsrLanguage.parse(null))
        assertSame(AsrLanguage.EN, AsrLanguage.parse(""))
        assertSame(AsrLanguage.EN, AsrLanguage.parse("xx"))
    }

    @Test
    fun parseRecognizesNamedValues() {
        assertSame(AsrLanguage.AUTO, AsrLanguage.parse("AUTO"))
        assertSame(AsrLanguage.JA, AsrLanguage.parse("JA"))
    }

    @Test
    fun languageCodesAndTags() {
        assertNull(AsrLanguage.AUTO.code)
        assertNull(AsrLanguage.AUTO.googleTag)
        assertEquals("en", AsrLanguage.EN.code)
        assertEquals("en-US", AsrLanguage.EN.googleTag)
        assertEquals("ja", AsrLanguage.JA.code)
        assertEquals("ja-JP", AsrLanguage.JA.googleTag)
    }

    @Test
    fun transcribeUrlAddsLanguageOnlyWhenPresent() {
        assertEquals("http://h:9/asr/transcribe", AsrHttp.transcribeUrl("http://h:9/", null))
        assertEquals("http://h:9/asr/transcribe", AsrHttp.transcribeUrl("http://h:9", ""))
        assertEquals("http://h:9/asr/transcribe?language=ja", AsrHttp.transcribeUrl("http://h:9/", "ja"))
    }

    @Test
    fun ankiConfigDefaultsAsrLanguageToEnglish() {
        assertEquals(AsrLanguage.EN, AnkiConfig(url = "", apiKey = "", deck = "").asrLanguage)
    }

    @Test
    fun sourceWiresLanguageThrough() {
        val factory = File("src/main/java/com/evan/voicesrs/RecognizerFactory.kt").readText()
        assert(factory.contains("GoogleRecognizer(context, cfg.asrLanguage.googleTag")) {
            "RecognizerFactory should pass cfg.asrLanguage.googleTag to GoogleRecognizer"
        }
        assert(factory.contains("cfg.asrLanguage.code")) {
            "RecognizerFactory should pass cfg.asrLanguage.code to RemoteWhisperRecognizer"
        }
        val prefs = File("src/main/java/com/evan/voicesrs/Prefs.kt").readText()
        assert(prefs.contains("KEY_ASR_LANGUAGE = \"asr_language\"")) {
            "Prefs should declare KEY_ASR_LANGUAGE = asr_language"
        }
    }
}
