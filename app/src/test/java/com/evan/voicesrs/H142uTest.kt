package com.evan.voicesrs

import java.io.File
import org.junit.Test

/** h142u: filler-words Settings controls. (a)-(b) FillerFilter.add/remove; (c) layout ids/order; (d) SettingsActivity wiring; (e) strings. */
class H142uTest {

    @Test
    fun `filler add normalizes, dedupes, ignores empty`() {
        assert(FillerFilter.add(listOf("um"), " So. ") == listOf("um", "so"))
        assert(FillerFilter.add(listOf("um"), "UM") == listOf("um"))
        assert(FillerFilter.add(listOf("um"), "  ") == listOf("um"))
    }

    @Test
    fun `filler remove is case-insensitive and no-op when absent`() {
        assert(FillerFilter.remove(listOf("um", "so"), "So") == listOf("um"))
        assert(FillerFilter.remove(listOf("um"), "x") == listOf("um"))
    }

    @Test
    fun `layout has filler controls after silence help`() {
        val layout = readFile("app/src/main/res/layout/activity_settings.xml")
        assert(layout.contains("ignoreFillersSwitch"))
        assert(layout.contains("fillerChips"))
        assert(layout.contains("fillerAddInput"))
        assert(layout.indexOf("ignoreFillersSwitch") > layout.indexOf("answerSilenceHelp"))
    }

    @Test
    fun `activity wires filler persist and filter calls`() {
        val src = readFile("app/src/main/java/com/evan/voicesrs/SettingsActivity.kt")
        assert(src.contains("persist { it.copy(ignoreFillers = checked) }"))
        assert(src.contains("FillerFilter.add("))
        assert(src.contains("FillerFilter.remove("))
    }

    @Test
    fun `strings define filler strings`() {
        val strings = readFile("app/src/main/res/values/strings.xml")
        assert(strings.contains("name=\"settings_ignore_fillers\""))
        assert(strings.contains("name=\"settings_ignore_fillers_summary\""))
        assert(strings.contains("name=\"settings_filler_add_hint\""))
    }
    private fun readFile(rel: String): String {
        var dir = File(".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists()) {
                val f = File(dir, rel)
                require(f.exists()) { "missing $rel" }
                return f.readText()
            }
            val parent = dir.parentFile
            requireNotNull(parent) { "project root not found from $dir" }
            dir = parent
        }
        throw IllegalStateException("project root not found from ${File(".").absoluteFile}")
    }
}
