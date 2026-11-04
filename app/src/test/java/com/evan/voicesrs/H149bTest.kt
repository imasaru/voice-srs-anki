package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h149b: the "Rate Ns" pill drawn during the spoken rating listen. */
class H149bTest {
    private fun readSrc(rel: String): String =
        listOf(rel, "app/$rel").firstOrNull { java.io.File(it).exists() }?.let { java.io.File(it).readText() }
            ?: throw IllegalStateException("$rel not found")

    private fun noHigh(label: String, s: String) {
        for (ch in s) {
            val cp = ch.code
            assertFalse("high code point in $label: U+" + Integer.toHexString(cp), cp > 0x2000)
        }
    }

    @Test fun bgRatePillExists() {
        val src = readSrc("src/main/res/drawable/bg_rate_pill.xml")
        assertTrue("missing colorTertiaryContainer", src.contains("?attr/colorTertiaryContainer"))
        assertTrue("missing radius 16dp", src.contains("android:radius=\"16dp\""))
        noHigh("bg_rate_pill.xml", src)
    }

    @Test fun cueLayoutHasRateCountdown() {
        val src = readSrc("src/main/res/layout/include_session_cue.xml")
        assertTrue("missing rateCountdown id", src.contains("android:id=\"@+id/rateCountdown\""))
        assertTrue("missing bg_rate_pill ref", src.contains("@drawable/bg_rate_pill"))
        assertTrue("missing ic_mic ref", src.contains("@drawable/ic_mic"))
        assertTrue("missing colorOnTertiaryContainer", src.contains("?attr/colorOnTertiaryContainer"))
        val rateId = src.indexOf("android:id=\"@+id/rateCountdown\"")
        assertTrue("visibility gone follows id", src.indexOf("android:visibility=\"gone\"", rateId) > rateId)
        val thinkId = src.indexOf("@+id/thinkCountdown")
        val pauseId = src.indexOf("@+id/chatPauseRow")
        assertTrue("after think", rateId > thinkId)
        assertTrue("before pause", rateId < pauseId)
        noHigh("include_session_cue.xml", src)
    }

    @Test fun renderCueCallsRenderRateCountdown() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val a = src.indexOf("private fun renderCue(")
        val b = src.indexOf("private fun renderRateCountdown(")
        assertTrue("renderRateCountdown func not found", b > a)
        assertTrue("renderCue calls renderRateCountdown", src.substring(a, b).contains("renderRateCountdown(state, drilling, silentOn)"))
    }

    @Test fun renderRateCountdownBody() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val a = src.indexOf("private fun renderRateCountdown(")
        val b = src.indexOf("/** Soft accent-pink pulse")
        assertTrue("soft accent comment not found", b > a)
        val body = src.substring(a, b)
        assertTrue("missing secondsLeft", body.contains("RateCountdown.secondsLeft(state.rateDeadlineMs, SystemClock.elapsedRealtime())"))
        assertTrue("missing visible", body.contains("RateCountdown.visible(drilling, silentOn, state.chatPaused, state.showBack, left)"))
        assertTrue("missing text", body.contains("RateCountdown.text(left)"))
        noHigh("renderRateCountdown", body)
    }

    @Test fun tickElapsedCallsRenderRateCountdown() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val a = src.indexOf("private fun tickElapsed()")
        val b = src.indexOf("private fun startElapsedTicker()")
        assertTrue("startElapsedTicker not found", b > a)
        assertTrue("tickElapsed calls renderRateCountdown", src.substring(a, b).contains("renderRateCountdown(state, state.drillRunning || DrillService.running, switchModel.on)"))
    }
}
