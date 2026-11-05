package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h153q: DiagLogActivity filled from approved mockup. */
class H153qTest {
    @Test fun a_layoutHasChipsHeaderListClear() {
        val lay = File("src/main/res/layout/activity_diag_log.xml").readText()
        assertTrue(lay.contains("@+id/diagLogLevelChips") || lay.contains("diagLogLevelChips"))
        assertTrue(lay.contains("TRACE") || lay.contains("diagLogChipTrace") || lay.contains("@string/settings_diag_log_level_trace"))
        assertTrue(lay.contains("@+id/diagLogList") || lay.contains("diagLogList"))
        assertTrue(lay.contains("diagLogHeaderTime") || lay.contains("Time"))
        assertFalse(lay.contains("diagLogPlaceholder")) // stub placeholder removed
        val item = File("src/main/res/layout/item_diag_log_row.xml").readText()
        assertTrue(item.contains("diagLogRowTime"))
        assertTrue(item.contains("diagLogRowLevel"))
        assertTrue(item.contains("diagLogRowMessage"))
        // clear action wired as an action view (no menu file)
        assertTrue(lay.contains("diagLogClear"))
    }

    @Test fun b_activityUsesDiagLogViewAndClear() {
        val src = File("src/main/java/com/evan/voicesrs/DiagLogActivity.kt").readText()
        assertTrue(src.contains("DiagLogView.readLast"))
        assertTrue(src.contains("DiagLogView.filter") || src.contains("DiagLogView.LEVELS"))
        assertTrue(src.contains("DiagLogView.clear"))
        assertTrue(src.contains("filesDir") || src.contains("\"logs\""))
        // no emoji glyphs in activity source
        assertFalse(src.contains("✓") || src.contains("✔") || src.contains("🗑") || src.contains("✨"))
    }

    @Test fun c_deleteIconAndClearString() {
        val ic = File("src/main/res/drawable/ic_delete.xml").readText()
        assertTrue(ic.contains("<vector") || ic.contains("path"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("settings_diag_log_clear") || strings.contains(">Clear<"))
        assertFalse(strings.contains("🗑"))
    }

    @Test fun d_defaultInfoChipIsCheckedInLayout() {
        val layout = File("src/main/res/layout/activity_diag_log.xml").readText()
        val info = layout.substringAfter("@+id/diagLogChipInfo").substringBefore("/>")
        assertTrue(info.contains("android:checked=\"true\""))
        assertTrue(info.contains("android:text=\"INFO\""))
        for (name in listOf("Trace", "Debug", "Warning", "Error")) {
            val chip = layout.substringAfter("@+id/diagLogChip$name").substringBefore("/>")
            assertTrue(chip.contains("android:checked=\"false\""))
        }
    }
}
