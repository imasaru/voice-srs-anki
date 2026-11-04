package com.evan.voicesrs

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
/** h138: no per-line play buttons in bubbles; read-aloud strips button labels and symbols. */
@RunWith(RobolectricTestRunner::class)
class H138Test {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val ctx: Context = app
    init { app.setTheme(R.style.Theme_VoiceSrs) }
    private fun buttons(v: View): Int = (if (v is Button) 1 else 0) + (if (v is ViewGroup) (0 until v.childCount).sumOf { buttons(v.getChildAt(it)) } else 0)
    private fun texts(v: View, a: MutableList<String>) { if (v is TextView) a.add(v.text.toString()); if (v is ViewGroup) (0 until v.childCount).forEach { texts(v.getChildAt(it), a) } }
    @Test fun `a symbols and emoji stripped`() {
        val out = SpeechStrip.clean("▶ Masking hides a column. 🔊\n■\n💬 Reply ⏸ now ✓")
        assertEquals("Masking hides a column.\nReply now", out)
        for (ch in out) assertTrue("unexpected U+%04X".format(ch.code), ch.code < 0x2190 || (ch.code > 0x2BFF && ch.code < 0x1F000))
    }
    @Test fun `b play button words dropped`() {
        val out = SpeechStrip.clean("Tap the play button to hear it")
        assertFalse("play button still present: '$out'", out.lowercase().contains("play button"))
    }
    @Test fun `c fillSections adds no per-line play buttons`() {
        val host = LinearLayout(ctx)
        CardFaceBinder.fillSections(host, TextView(ctx), "Masking hides a column. It runs at query time.", "", turnId = 1L, onSpeak = { _, _ -> })
        assertEquals("no button views", 0, buttons(host))
        val t = mutableListOf<String>(); texts(host, t)
        t.forEach { assertFalse("section glyph: '$it'", it == "▶" || it == "■" || it == "…") }
    }
    @Test fun `d visibleText skips a Button child`() {
        val v = LinearLayout(ctx)
        v.addView(Button(ctx).also { it.text = "▶" })
        v.addView(TextView(ctx).also { it.text = "Hello" })
        assertEquals("Hello", BubbleMenu.visibleText(v))
    }
    @Test fun `e source dropped the inline section button`() {
        fun read(name: String) = listOf("src/main/java/com/evan/voicesrs/$name", "app/src/main/java/com/evan/voicesrs/$name").first { java.io.File(it).exists() }.let { java.io.File(it).readText() }
        val card = read("CardFaceBinder.kt")
        assertFalse("CardFaceBinder still has a ▶ glyph", card.contains("▶"))
        assertFalse("CardFaceBinder still references playback_read_section", card.contains("R.string.playback_read_section"))
        assertTrue("ChatPane should clean read-aloud text", read("ChatPane.kt").contains("SpeechStrip.clean("))
    }
}
