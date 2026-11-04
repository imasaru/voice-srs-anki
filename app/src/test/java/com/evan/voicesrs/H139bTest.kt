package com.evan.voicesrs

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
/** h139b: Material vector icons instead of emoji on the thread link, voice line and add chip. */
@RunWith(RobolectricTestRunner::class)
class H139bTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val ctx: Context = app
    private val resources: Resources = ctx.resources
    init { app.setTheme(R.style.Theme_VoiceSrs) }

    private fun exists(rel: String): Boolean =
        listOf("src/main/$rel", "app/src/main/$rel").any { java.io.File(it).exists() }
    private fun read(rel: String): String =
        listOf("src/main/$rel", "app/src/main/$rel").first { java.io.File(it).exists() }.let { java.io.File(it).readText() }

    private fun count(needle: String, s: String): Int =
        if (needle.isEmpty()) 0 else s.split(needle).size - 1

    @Test fun `a thread label and string drop the glyph`() {
        assertEquals("Reply in thread", ThreadLabel.text(0, null, java.time.ZoneOffset.UTC))
        assertEquals("Reply in thread", ctx.getString(R.string.thread_reply))
    }

    @Test fun `b voice line drops the speaker glyph`() {
        assertTrue(
            "voice line should start with \"Voice: \"",
            VoiceStatusLine.format(EngineChoice.ON_DEVICE, "English").startsWith("Voice: ")
        )
    }

    @Test fun `c source and strings carry none of the old glyphs`() {
        val files = listOf(
            "java/com/evan/voicesrs/ThreadStore.kt",
            "java/com/evan/voicesrs/VoiceStatusLine.kt",
            "java/com/evan/voicesrs/ActionRowState.kt",
            "java/com/evan/voicesrs/PlaybackControl.kt",
            "java/com/evan/voicesrs/MainActivity.kt",
            "res/values/strings.xml",
        )
        val s = files.joinToString("\n") { read(it) }
        val glyphs = listOf("💬", "🔊", "⏸", "▶", "＋")
        for (g in glyphs)
            assertFalse("glyph '$g' remains", s.contains(g))
    }

    @Test fun `d thread link uses the forum drawable`() {
        val layout = read("res/layout/item_chat_turn.xml")
        assertTrue("thread link should reference ic_forum", layout.contains("@drawable/ic_forum"))
        assertTrue("missing res/drawable/ic_forum.xml", exists("res/drawable/ic_forum.xml"))
    }

    @Test fun `e MainActivity wires the add and volume icons`() {
        val main = read("java/com/evan/voicesrs/MainActivity.kt")
        assertTrue("no ic_add ref", main.contains("R.drawable.ic_add"))
        assertTrue("no ic_volume_up ref", main.contains("R.drawable.ic_volume_up"))
    }
}
