package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** h156a: Settings has a Read aloud over Media / Call toggle wired to Prefs.readAloudChannel. */
class H156aTest {
    private fun f(p: String) = File("src/main/$p").readText()

    @Test fun a_layoutHasToggleAfterTokenStatus() {
        val x = f("res/layout/activity_settings.xml")
        val tok = x.indexOf("@+id/tokenStatus")
        val grp = x.indexOf("@+id/readAloudChannelGroup")
        assertTrue("tokenStatus missing", tok >= 0)
        assertTrue("toggle group missing or before tokenStatus", grp > tok)
        assertTrue(x.indexOf("@+id/readAloudMedia") > grp)
        assertTrue(x.indexOf("@+id/readAloudCall") > x.indexOf("@+id/readAloudMedia"))
        assertEquals(1, Regex("@\\+id/readAloudChannelGroup").findAll(x).count())
        val card = x.indexOf("</com.google.android.material.card.MaterialCardView>", tok)
        assertTrue("toggle must stay inside the Speech card", x.indexOf("@+id/readAloudCall") < card)
    }

    @Test fun b_stringsPresent() {
        val s = f("res/values/strings.xml")
        assertTrue(s.contains("name=\"settings_read_aloud_over\">Read aloud over<"))
        assertTrue(s.contains("name=\"settings_read_aloud_media\">Media<"))
        assertTrue(s.contains("name=\"settings_read_aloud_call\">Call<"))
        assertTrue(s.contains("name=\"settings_read_aloud_help\">"))
    }

    @Test fun c_settingsWiresPrefs() {
        val k = f("java/com/evan/voicesrs/SettingsActivity.kt")
        val at = k.indexOf("findViewById(R.id.readAloudChannelGroup)")
        assertTrue("group lookup missing", at >= 0)
        val body = k.substring(at, minOf(k.length, at + 700))
        assertTrue(body.contains("held.readAloudChannel == Prefs.READ_ALOUD_CALL"))
        assertTrue(body.contains("R.id.readAloudCall else R.id.readAloudMedia"))
        assertTrue(body.contains("Prefs.READ_ALOUD_CALL else Prefs.READ_ALOUD_MEDIA"))
        assertTrue(body.contains("persist { it.copy(readAloudChannel = ch) }"))
        assertTrue("must come before offlineSwitch", at < k.indexOf("offlineSwitch.isChecked = held.offlineBatch"))
    }
}
