package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** h104b: Settings > Drill switch "Auto-accept suggested rating". Pure source-scan, no Android. */
class H104bTest {
    @Test
    fun prefsStoresAutoAcceptRating() {
        val src = File("src/main/java/com/evan/voicesrs/Prefs.kt").readText()
        assertTrue(src.contains("val autoAcceptRating: Boolean = true"))
        assertTrue(src.contains("getBoolean(KEY_AUTO_ACCEPT_RATING, true)"))
        assertTrue(src.contains("putBoolean(KEY_AUTO_ACCEPT_RATING, cfg.autoAcceptRating)"))
    }

    @Test
    fun resourcesExposeSwitch() {
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains(">Auto-accept suggested rating<"))
        val layout = File("src/main/res/layout/activity_settings.xml").readText()
        assertTrue(layout.contains("@+id/autoAcceptRatingSwitch"))
    }

    @Test
    fun settingsActivityWiresSwitch() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue(src.contains("persist { it.copy(autoAcceptRating = checked) }"))
        assertTrue(src.contains("autoAcceptRatingSwitch.isChecked = held.autoAcceptRating"))
    }

    @Test
    fun drillServiceRespectsAutoAccept() {
        val src = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()
        assertTrue(src.contains("if (auto != null && !screenCard && autoOn) {"))
        assertTrue(src.contains("if (auto != null && !screenCard && !autoOn) {"))
        assertTrue(src.contains("GradeConfirm.countdownStatus(mapped, left)"))
    }
}
