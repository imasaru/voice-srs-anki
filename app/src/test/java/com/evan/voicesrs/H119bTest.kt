package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H119bTest {

    @Test
    fun menuItemsPutsAddRowLast() {
        assertEquals(listOf("+ Add"), AsrServers.menuItems(emptyList(), "+ Add"))
        assertEquals(
            listOf("https://a", "https://b", "+ Add"),
            AsrServers.menuItems(listOf("https://a", "https://b"), "+ Add")
        )
    }

    @Test
    fun isAddRowIsLastPosition() {
        assertTrue(AsrServers.isAddRow(2, listOf("https://a", "https://b")))
        assertFalse(AsrServers.isAddRow(1, listOf("https://a", "https://b")))
        assertTrue(AsrServers.isAddRow(0, emptyList()))
    }

    @Test
    fun layoutUsesExposedDropdownAndMaterialAutoComplete() {
        val xml = File("src/main/res/layout/activity_settings.xml").readText()
        val a = xml.indexOf("@+id/asrUrlLayout")
        val b = xml.indexOf("@+id/asrUrlInput")
        assertTrue(a > 0)
        assertTrue(b > a)
        val head = xml.substring(a, b)
        assertTrue(head.contains("ExposedDropdownMenu"))
        assertTrue(head.contains("MaterialAutoCompleteTextView"))
        val tail = xml.substring(b, xml.indexOf("/>", b))
        assertTrue(tail.contains("textUri"))
        assertTrue(tail.contains("actionDone"))
    }

    @Test
    fun settingsActivityWiresDropdown() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue(src.contains("MaterialAutoCompleteTextView = findViewById(R.id.asrUrlInput)"))
        assertTrue(src.contains("SavedUrls.menuItems(saved(), addLabel)"))
        assertTrue(src.contains("SavedUrls.isAddRow(position, saved())"))
        assertTrue(src.contains("SavedUrls.remember(it.asrServers, raw)"))
    }

    @Test
    fun stringsHasAddServerLabel() {
        val xml = File("src/main/res/values/strings.xml").readText()
        assertTrue(xml.contains("name=\"settings_asr_add_server\""))
    }
}
