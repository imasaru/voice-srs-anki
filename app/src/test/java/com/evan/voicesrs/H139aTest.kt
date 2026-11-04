package com.evan.voicesrs

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
/** h139a: Material vector icons instead of emoji on drill Start/Stop + Pause/Resume buttons and prompt-chip screens. */
@RunWith(RobolectricTestRunner::class)
class H139aTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val ctx: Context = app
    init { app.setTheme(R.style.Theme_VoiceSrs) }

    private fun exists(rel: String): Boolean =
        listOf("src/main/$rel", "app/src/main/$rel").any { java.io.File(it).exists() }
    private fun read(rel: String): String =
        listOf("src/main/$rel", "app/src/main/$rel").first { java.io.File(it).exists() }.let { java.io.File(it).readText() }

    private fun count(needle: String, s: String): Int =
        if (needle.isEmpty()) 0 else s.split(needle).size - 1

    @Test fun `a button strings drop the glyph`() {
        assertEquals("Start drill", ctx.getString(R.string.app_start))
        assertEquals("Stop drill", ctx.getString(R.string.app_stop))
        assertEquals("Pause", ctx.getString(R.string.drill_pause))
        assertEquals("Resume", ctx.getString(R.string.drill_resume))
        assertEquals("Add prompt", ctx.getString(R.string.prompt_chips_add))
    }

    @Test fun `b strings carry none of the old glyphs`() {
        val s = read("res/values/strings.xml")
        for (ch in listOf('▶', '⏹', '⏸', '＋', 'ⓘ'))
            assertFalse("strings.xml still has U+%04X".format(ch.code), s.contains(ch))
    }

    @Test fun `c chip row is four ImageButtons, no old buttons or glyphs`() {
        val row = read("res/layout/item_prompt_chip_row.xml")
        assertEquals("expected 4 ImageButtons", 4, count("<ImageButton", row))
        assertFalse("a plain <Button remains", row.contains("<Button"))
        assertFalse("▲ remains", row.contains("▲"))
        assertFalse("▼ remains", row.contains("▼"))
        assertFalse("✎ remains", row.contains("✎"))
        assertFalse("✕ remains", row.contains("✕"))
    }

    @Test fun `d seven drawables copied in`() {
        for (name in listOf(
            "ic_play_arrow.xml", "ic_add.xml", "ic_arrow_upward.xml",
            "ic_arrow_downward.xml", "ic_edit.xml", "ic_close.xml", "ic_info_outline.xml"
        ))
            assertTrue("missing res/drawable/$name", exists("res/drawable/$name"))
    }

    @Test fun `e MainActivity wires the new icons`() {
        val main = read("java/com/evan/voicesrs/MainActivity.kt")
        assertTrue("no ic_play_arrow ref", main.contains("R.drawable.ic_play_arrow"))
        assertTrue("no ic_drill_pause ref", main.contains("R.drawable.ic_drill_pause"))
    }

    @Test fun `f promptRowEdit inflates as an ImageButton`() {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_prompt_chip_row, null)
        assertTrue("promptRowEdit is not an ImageButton",
            v.findViewById<View>(R.id.promptRowEdit) is ImageButton)
    }
}
