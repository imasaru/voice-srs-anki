package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h148c: on-device TTS sets USAGE_ASSISTANT + CONTENT_TYPE_SPEECH, matching EdgeSpeaker. */
class H148cTest {
    @Test fun onDeviceTtsUsesAssistantAttributes() {
        val src = drillServiceSrc()
        val start = src.indexOf("private fun initTts() {")
        assertTrue("initTts not found", start >= 0)
        val end = src.indexOf("private fun applyTtsPrefs()")
        assertTrue("applyTtsPrefs not found", end >= 0)
        val body = src.substring(start, end)
        assertTrue("missing setAudioAttributes", body.contains("tts?.setAudioAttributes("))
        assertTrue("missing USAGE_ASSISTANT", body.contains("AudioAttributes.USAGE_ASSISTANT"))
        assertTrue("missing CONTENT_TYPE_SPEECH", body.contains("AudioAttributes.CONTENT_TYPE_SPEECH"))
        val lang = body.indexOf("tts?.language = Locale.US")
        val attrs = body.indexOf("tts?.setAudioAttributes(")
        assertTrue("setAudioAttributes must come after language", attrs > lang)
    }

    private fun drillServiceSrc(): String {
        for (c in listOf("src/main/java/com/evan/voicesrs/DrillService.kt", "app/src/main/java/com/evan/voicesrs/DrillService.kt")) {
            val f = java.io.File(c)
            if (f.exists()) return f.readText()
        }
        throw IllegalStateException("DrillService.kt not found")
    }
}
